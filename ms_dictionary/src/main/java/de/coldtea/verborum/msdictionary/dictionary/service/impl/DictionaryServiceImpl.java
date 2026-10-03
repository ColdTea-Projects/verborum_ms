package de.coldtea.verborum.msdictionary.dictionary.service.impl;

import de.coldtea.verborum.msdictionary.common.event.DictionaryDeletedEvent;
import de.coldtea.verborum.msdictionary.common.event.DictionarySnapshotEntry;
import de.coldtea.verborum.msdictionary.common.event.DictionarySnapshotEvent;
import de.coldtea.verborum.msdictionary.common.event.DictionaryUpdatedEvent;
import de.coldtea.verborum.msdictionary.common.event.DictionaryVisibilityEvent;
import de.coldtea.verborum.msdictionary.common.event.OutboundEvent;
import de.coldtea.verborum.msdictionary.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msdictionary.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msdictionary.common.mapper.DictionaryMapper;
import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryRequestDTO;
import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryResponseDTO;
import de.coldtea.verborum.msdictionary.dictionary.entity.Dictionary;
import de.coldtea.verborum.msdictionary.dictionary.repository.DictionaryRepository;
import de.coldtea.verborum.msdictionary.dictionary.service.DictionaryService;
import de.coldtea.verborum.msdictionary.word.repository.WordRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_DELETED;
import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_SNAPSHOT;
import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_UPDATED;
import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_VISIBILITY_PRIVATE;
import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC;
import static de.coldtea.verborum.msdictionary.common.constants.ErrorMessageConstants.DICTIONARY_WAS_NOT_FOUND_ID;
import static de.coldtea.verborum.msdictionary.common.constants.ErrorMessageConstants.NOT_THE_OWNER;
import static de.coldtea.verborum.msdictionary.common.utils.DictionaryAccessUtils.isReadableBy;

@Service
@RequiredArgsConstructor
public class DictionaryServiceImpl implements DictionaryService {

    private final DictionaryRepository dictionaryRepository;

    private final WordRepository wordRepository;

    private final DictionaryMapper dictionaryMapper;

    // Not RabbitTemplate: the service raises application events and OutboundEventPublisher sends
    // them after commit (rule 1 in docs/agent/rabbitmq.md)
    private final ApplicationEventPublisher eventPublisher;


    @Transactional
    @Override
    public DictionaryResponseDTO saveDictionary(DictionaryRequestDTO dictionaryRequestDTO, String ownerId) {
        // P3-05: the owner comes from the token. A body naming someone else is rejected rather than
        // silently rewritten, so a mis-migrated client fails loudly instead of writing under the
        // wrong owner
        if (dictionaryRequestDTO.getUserId() != null && !ownerId.equals(dictionaryRequestDTO.getUserId())) {
            throw new ForbiddenOperationException(NOT_THE_OWNER);
        }

        Optional<Dictionary> existing = dictionaryRepository.findById(dictionaryRequestDTO.getDictionaryId());

        // saveDictionary() backs POST and PUT, and the client supplies the id — so without this an
        // authenticated caller could POST someone else's dictionaryId and take the row over
        if (existing.isPresent() && !ownerId.equals(existing.get().getUserId())) {
            throw new ForbiddenOperationException(NOT_THE_OWNER);
        }

        // Read the current visibility before saving over it — a dictionary that is absent has
        // never been public, so it counts as private
        boolean wasPublic = existing
                .map(dictionary -> Boolean.TRUE.equals(dictionary.getIsPublic()))
                .orElse(false);

        // Copied out now, not read from `existing` after the save: saveAndFlush merges the new values
        // onto that same managed instance, so afterwards it would always compare equal to the save
        ListingFields previousListing = existing.map(ListingFields::of).orElse(null);

        Dictionary dictionary = dictionaryMapper.toDictionary(dictionaryRequestDTO);
        dictionary.setUserId(ownerId);

        Dictionary savedDictionary = dictionaryRepository.saveAndFlush(dictionary);

        // Raising the event only queues it; OutboundEventPublisher sends after this transaction
        // commits (rule 1). Ordering inside the method no longer matters for correctness — the old
        // "keep the send last" comment was mitigation for publishing inside the transaction.
        publishVisibilityChange(savedDictionary, wasPublic);
        publishListingUpdate(savedDictionary, wasPublic, previousListing);

        return dictionaryMapper.toDictionaryResponseDTO(savedDictionary);
    }

    /**
     * The fields ms_marketplace lists. A change to any other field (or a plain re-save) is invisible
     * to the marketplace and must not produce an event.
     */
    private record ListingFields(String name, String fromLang, String toLang) {
        static ListingFields of(Dictionary dictionary) {
            return new ListingFields(dictionary.getName(), dictionary.getFromLang(), dictionary.getToLang());
        }
    }

    /**
     * Publishes `dictionary.updated` when a dictionary that was public and still is changes a listed
     * field (P4-03). Before this, a rename of a public dictionary emitted nothing and the marketplace
     * listing kept the old name indefinitely.
     * <p>
     * A visibility flip is not an update: going public or private already carries the full payload on
     * its own event, so publishing both would make the consumer process the same change twice.
     */
    private void publishListingUpdate(Dictionary dictionary, boolean wasPublic, ListingFields previousListing) {
        boolean isPublic = Boolean.TRUE.equals(dictionary.getIsPublic());
        if (!wasPublic || !isPublic || ListingFields.of(dictionary).equals(previousListing)) {
            return;
        }

        eventPublisher.publishEvent(new OutboundEvent(
                ROUTING_KEY_DICTIONARY_UPDATED,
                DictionaryUpdatedEvent.builder()
                        .dictionaryId(dictionary.getDictionaryId())
                        .userId(dictionary.getUserId())
                        .fromLang(dictionary.getFromLang())
                        .toLang(dictionary.getToLang())
                        .dictionaryName(dictionary.getName())
                        // Ordering key (rule 4) — see publishVisibilityChange
                        .updatedAt(dictionary.getUpdatedAt())
                        .eventTimestamp(OffsetDateTime.now())
                        .build()));
    }

    /**
     * Publishes every public dictionary as one `dictionary.snapshot` — the reconciliation backstop
     * for lost events (rule 6, P4-03). Triggered by {@code DictionarySnapshotScheduler}.
     * <p>
     * `takenAt` is read before the query, so anything the consumer holds that changed after it is
     * known to be newer than the snapshot and is left alone.
     */
    @Transactional
    @Override
    public void publishPublicSnapshot() {
        OffsetDateTime takenAt = OffsetDateTime.now();

        List<DictionarySnapshotEntry> entries = dictionaryRepository.findByIsPublicTrue().stream()
                .map(dictionary -> DictionarySnapshotEntry.builder()
                        .dictionaryId(dictionary.getDictionaryId())
                        .userId(dictionary.getUserId())
                        .fromLang(dictionary.getFromLang())
                        .toLang(dictionary.getToLang())
                        .dictionaryName(dictionary.getName())
                        .updatedAt(dictionary.getUpdatedAt())
                        .build())
                .toList();

        // Published even when empty: an empty snapshot is the true statement "nothing is public",
        // and it is what lets the marketplace clear listings whose removal events were lost
        eventPublisher.publishEvent(new OutboundEvent(
                ROUTING_KEY_DICTIONARY_SNAPSHOT,
                DictionarySnapshotEvent.builder()
                        .takenAt(takenAt)
                        .dictionaries(entries)
                        .eventTimestamp(OffsetDateTime.now())
                        .build()));
    }

    /**
     * Publishes only when `is_public` actually flips. saveDictionary() backs both POST and PUT,
     * so re-saving a public dictionary (e.g. a rename) would otherwise re-announce it as public
     * and have ms_marketplace create a duplicate listing.
     */
    private void publishVisibilityChange(Dictionary dictionary, boolean wasPublic) {
        boolean isPublic = Boolean.TRUE.equals(dictionary.getIsPublic());
        if (isPublic == wasPublic) {
            return;
        }

        eventPublisher.publishEvent(new OutboundEvent(
                isPublic ? ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC : ROUTING_KEY_DICTIONARY_VISIBILITY_PRIVATE,
                DictionaryVisibilityEvent.builder()
                        .dictionaryId(dictionary.getDictionaryId())
                        .userId(dictionary.getUserId())
                        .isPublic(isPublic)
                        .fromLang(dictionary.getFromLang())
                        .toLang(dictionary.getToLang())
                        .dictionaryName(dictionary.getName())
                        // The projection's ordering key (rule 4): a consumer must ignore an event
                        // older than the state it already holds, or two quick renames delivered out
                        // of order leave the listing permanently stale
                        .updatedAt(dictionary.getUpdatedAt())
                        .eventTimestamp(OffsetDateTime.now())
                        .build()));
    }

    @Transactional
    @Override
    public void deleteDictionary(String dictionaryId, String ownerId) {
        // P3-08: ids are guessable and were the whole authorisation story here — before this, any
        // authenticated caller could delete any dictionary by id. Checked before anything is
        // touched; an unknown id stays a silent 200 rather than revealing which ids exist
        dictionaryRepository.findById(dictionaryId)
                .filter(existing -> !ownerId.equals(existing.getUserId()))
                .ifPresent(existing -> {
                    throw new ForbiddenOperationException(NOT_THE_OWNER);
                });

        deleteDictionaryInternal(dictionaryId);
    }

    /**
     * Ownership-free deletion, used by the checked entry point above and by the `user.deleted`
     * cascade, where the actor is ms_user rather than a logged-in caller.
     */
    private void deleteDictionaryInternal(String dictionaryId) {
        // Read before deleting: the event carries userId, and an absent dictionary must not
        // announce a deletion that never happened. deleteById() is a silent no-op on a missing
        // row in Spring Data JPA 3.x, so this stays a 200 either way
        Dictionary dictionary = dictionaryRepository.findById(dictionaryId).orElse(null);

        // Words reference the dictionary without a DB-level FK — delete them explicitly.
        // Deliberately runs even when the dictionary row is already gone: words can be orphaned
        // (no FK to stop it), and this is what cleans them up. Do not move the null-check above
        // this line
        wordRepository.deleteByDictionaryIdIn(List.of(dictionaryId));
        dictionaryRepository.deleteById(dictionaryId);

        if (dictionary == null) {
            return;
        }

        eventPublisher.publishEvent(new OutboundEvent(
                ROUTING_KEY_DICTIONARY_DELETED,
                DictionaryDeletedEvent.builder()
                        .dictionaryId(dictionary.getDictionaryId())
                        .userId(dictionary.getUserId())
                        .eventTimestamp(OffsetDateTime.now())
                        .build()));
    }

    /**
     * Cascade for a deleted user (roadmap P2-10), driven by the `user.deleted` event.
     * <p>
     * `userId` here is the JWT subject stored in `fk_user_id` — the caller must pass the event's
     * `keycloakId`, not ms_user's `userId`. See UserEventListener.
     * <p>
     * Idempotent by construction: a user with no dictionaries deletes nothing and throws nothing, so
     * a redelivered event is harmless. No `dictionary.deleted` events are published for the removed
     * rows — ms_marketplace consumes `user.deleted` itself (see the routing table in verborum.md),
     * so re-announcing each dictionary would duplicate work it is already doing.
     */
    @Transactional
    @Override
    public void deleteAllByUserId(String userId) {
        List<String> dictionaryIds = dictionaryRepository.findByUserId(userId).stream()
                .map(Dictionary::getDictionaryId)
                .toList();

        if (dictionaryIds.isEmpty()) {
            return;
        }

        // Words have no DB-level FK to their dictionary, so they must go explicitly and first —
        // same reasoning as deleteDictionary()
        wordRepository.deleteByDictionaryIdIn(dictionaryIds);
        // deleteByDictionaryIdIn, not deleteAllById: the latter loads and deletes one row at a time
        dictionaryRepository.deleteByDictionaryIdIn(dictionaryIds);
    }

    @Override
    public List<DictionaryResponseDTO> getDictionariesByUser(String userId) {
        return dictionaryRepository.findByUserId(userId).stream().map(dictionaryMapper::toDictionaryResponseDTO).toList();
    }

    @Override
    public DictionaryResponseDTO getDictionaryById(String dictionaryId, String ownerId) {
        Dictionary dictionary = dictionaryRepository.findById(dictionaryId)
                .orElseThrow(() -> new RecordNotFoundException(DICTIONARY_WAS_NOT_FOUND_ID + dictionaryId));

        // Readable = owned or public (P4-10). Otherwise 404, not 403: a caller must not be able to
        // tell someone's private dictionary from one that does not exist (P3-08)
        if (!isReadableBy(dictionary, ownerId)) {
            throw new RecordNotFoundException(DICTIONARY_WAS_NOT_FOUND_ID + dictionaryId);
        }

        return dictionaryMapper.toDictionaryResponseDTO(dictionary);
    }

    @Override
    public List<DictionaryResponseDTO> getDictionariesByIds(List<String> dictionaryIds, String ownerId) {
        return dictionaryRepository.findAllById(dictionaryIds).stream()
                .filter(dictionary -> isReadableBy(dictionary, ownerId))
                .map(dictionaryMapper::toDictionaryResponseDTO)
                .toList();
    }

}
