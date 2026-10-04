package de.coldtea.verborum.msmarketplace.dictionarystats.service.impl;

import de.coldtea.verborum.msmarketplace.common.event.DictionaryDeletedEvent;
import de.coldtea.verborum.msmarketplace.common.event.DictionarySnapshotEntry;
import de.coldtea.verborum.msmarketplace.common.event.DictionarySnapshotEvent;
import de.coldtea.verborum.msmarketplace.common.event.DictionaryUpdatedEvent;
import de.coldtea.verborum.msmarketplace.common.event.DictionaryVisibilityEvent;
import de.coldtea.verborum.msmarketplace.common.mapper.DictionaryStatsMapper;
import de.coldtea.verborum.msmarketplace.common.response.SliceResponse;
import de.coldtea.verborum.msmarketplace.common.utils.LanguagePairUtils;
import de.coldtea.verborum.msmarketplace.dictionaryimport.entity.DictionaryImport;
import de.coldtea.verborum.msmarketplace.dictionaryimport.repository.DictionaryImportRepository;
import de.coldtea.verborum.msmarketplace.dictionarystats.dto.DictionaryListingResponseDTO;
import de.coldtea.verborum.msmarketplace.dictionarystats.dto.ListingFilter;
import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import de.coldtea.verborum.msmarketplace.dictionarystats.repository.DictionaryStatsRepository;
import de.coldtea.verborum.msmarketplace.dictionarystats.service.DictionaryStatsService;
import de.coldtea.verborum.msmarketplace.publisher.entity.Publisher;
import de.coldtea.verborum.msmarketplace.publisher.repository.PublisherRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static de.coldtea.verborum.msmarketplace.common.utils.LanguagePairUtils.toLangPair;
import static de.coldtea.verborum.msmarketplace.common.utils.LikePatternUtils.toContainsPattern;
import static de.coldtea.verborum.msmarketplace.common.utils.ResponseUtils.toSliceResponse;
import static de.coldtea.verborum.msmarketplace.dictionarystats.repository.DictionaryStatsSpecifications.hasAnyTag;
import static de.coldtea.verborum.msmarketplace.dictionarystats.repository.DictionaryStatsSpecifications.hasLangPairIn;
import static de.coldtea.verborum.msmarketplace.dictionarystats.repository.DictionaryStatsSpecifications.hasNamedPublisher;
import static de.coldtea.verborum.msmarketplace.dictionarystats.repository.DictionaryStatsSpecifications.isListed;
import static de.coldtea.verborum.msmarketplace.dictionarystats.repository.DictionaryStatsSpecifications.isPublishedBy;

/**
 * Every write here is "apply this state if it is newer than what I hold" (rule 4), with the
 * dictionary's own `updatedAt` as the clock. Visibility is part of that state: going private hides
 * the row rather than deleting it, so the hidden row's `sourceUpdatedAt` keeps rejecting older
 * "public" state — a delayed public event, or a snapshot read just before the flip. Deleting it
 * would leave nothing to compare against, and the older state would re-list a dictionary its owner
 * had just made private.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DictionaryStatsServiceImpl implements DictionaryStatsService {

    // Stable order for paging: without a unique last key, rows that tie on the sort column can swap
    // between two page requests and appear on both pages or on neither
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.asc("dictionaryId"));
    private static final Sort MOST_IMPORTED_FIRST =
            Sort.by(Sort.Order.desc("importCount"), Sort.Order.desc("publishedAt"), Sort.Order.asc("dictionaryId"));

    private final DictionaryStatsRepository dictionaryStatsRepository;

    private final DictionaryStatsMapper dictionaryStatsMapper;

    private final DictionaryImportRepository dictionaryImportRepository;

    // Display names (P4-13): read for browse, removed on user.deleted. Written by PublisherService
    private final PublisherRepository publisherRepository;

    @Override
    public SliceResponse<DictionaryListingResponseDTO> getListings(ListingFilter filter, int page, int size) {
        return browse(toSpecification(filter), PageRequest.of(page, size, NEWEST_FIRST));
    }

    @Override
    public SliceResponse<DictionaryListingResponseDTO> getPopularListings(ListingFilter filter, int page, int size) {
        return browse(toSpecification(filter), PageRequest.of(page, size, MOST_IMPORTED_FIRST));
    }

    @Override
    public SliceResponse<DictionaryListingResponseDTO> getListingsByPublisher(String publisherId, int page, int size) {
        // A publisher without a display name has no visible listings here either (P4-13)
        return browse(isListed().and(hasNamedPublisher(null)).and(isPublishedBy(publisherId)),
                PageRequest.of(page, size, NEWEST_FIRST));
    }

    @Transactional
    @Override
    public void publishListing(DictionaryVisibilityEvent event) {
        OffsetDateTime sourceUpdatedAt = sourceUpdatedAt(event.getUpdatedAt(), event.getEventTimestamp());
        Optional<DictionaryStats> existing = dictionaryStatsRepository.findById(event.getDictionaryId());

        if (existing.isEmpty()) {
            dictionaryStatsRepository.saveAndFlush(newRow(event.getDictionaryId(), event.getUserId(),
                    event.getDictionaryName(), event.getFromLang(), event.getToLang(), event.getTags(), sourceUpdatedAt, true));
            return;
        }

        // Upsert, never a second insert (rule 3) — and a redelivered or overtaken event changes nothing
        DictionaryStats listing = existing.get();
        if (!isNewer(sourceUpdatedAt, listing)) {
            return;
        }

        applyPublicState(listing, event.getUserId(), event.getDictionaryName(), event.getFromLang(),
                event.getToLang(), event.getTags(), sourceUpdatedAt);
        dictionaryStatsRepository.saveAndFlush(listing);
    }

    @Transactional
    @Override
    public void hideListing(DictionaryVisibilityEvent event) {
        OffsetDateTime sourceUpdatedAt = sourceUpdatedAt(event.getUpdatedAt(), event.getEventTimestamp());
        Optional<DictionaryStats> existing = dictionaryStatsRepository.findById(event.getDictionaryId());

        // No row yet: the private event overtook the public one, or the public one was lost. Record a
        // hidden row anyway — it is what makes that older public event compare as stale when it lands
        if (existing.isEmpty()) {
            dictionaryStatsRepository.saveAndFlush(newRow(event.getDictionaryId(), event.getUserId(),
                    event.getDictionaryName(), event.getFromLang(), event.getToLang(), event.getTags(), sourceUpdatedAt, false));
            return;
        }

        DictionaryStats listing = existing.get();
        if (!isNewer(sourceUpdatedAt, listing)) {
            return;
        }

        applyListingFields(listing, event.getUserId(), event.getDictionaryName(), event.getFromLang(),
                event.getToLang(), event.getTags(), sourceUpdatedAt);
        // importCount and publishedAt are kept: they are history, and survive a later re-publish
        listing.setIsListed(false);
        dictionaryStatsRepository.saveAndFlush(listing);
    }

    @Transactional
    @Override
    public void updateListing(DictionaryUpdatedEvent event) {
        OffsetDateTime sourceUpdatedAt = sourceUpdatedAt(event.getUpdatedAt(), event.getEventTimestamp());

        // Update-only on purpose: see the interface. filter() drops stale deliveries (rule 4).
        // A newer update on a hidden row re-lists it: ms_dictionary only sends dictionary.updated for a
        // dictionary that is public, so it proves the dictionary went public again after it was hidden
        // — even if that public event has not arrived (or was lost)
        dictionaryStatsRepository.findById(event.getDictionaryId())
                .filter(listing -> isNewer(sourceUpdatedAt, listing))
                .ifPresent(listing -> {
                    applyPublicState(listing, event.getUserId(), event.getDictionaryName(), event.getFromLang(),
                            event.getToLang(), event.getTags(), sourceUpdatedAt);
                    dictionaryStatsRepository.saveAndFlush(listing);
                });
    }

    /**
     * A deletion carries no `updatedAt` — the dictionary is gone. Its `eventTimestamp` stands in: it
     * is taken on ms_dictionary's clock inside the deleting transaction, so it is later than every
     * `updatedAt` that dictionary ever had, and older public state compares as stale against it.
     * <p>
     * With no row there is nothing to hide, and the event lacks the name and languages a hidden row
     * needs. A late, older public event could then list the deleted dictionary until the next
     * snapshot, which removes it (absent, and older than `takenAt`).
     */
    @Transactional
    @Override
    public void hideDeletedListing(DictionaryDeletedEvent event) {
        OffsetDateTime deletedAt = event.getEventTimestamp();

        dictionaryStatsRepository.findById(event.getDictionaryId())
                .filter(listing -> isNewer(deletedAt, listing))
                .ifPresent(listing -> {
                    listing.setIsListed(false);
                    listing.setSourceUpdatedAt(deletedAt);
                    dictionaryStatsRepository.saveAndFlush(listing);
                });
    }

    /**
     * Deleted outright, not hidden: `user.deleted` is timed on ms_user's clock, which cannot be
     * compared with ms_dictionary's `updatedAt`, so a hidden row would guard nothing reliably. And
     * nothing needs guarding — ms_dictionary deletes the same user's dictionaries on the same event,
     * so no newer public state for them can follow.
     * <p>
     * Idempotent: a redelivery finds no rows and deletes nothing.
     */
    @Transactional
    @Override
    public void deleteListingsByUser(String keycloakId) {
        // As an importer (P4-07): the record of what they imported is their data too. Counts are left
        // as they are — popularity is history, and nothing identifies who contributed to it any more
        List<DictionaryImport> imported = dictionaryImportRepository.findByUserId(keycloakId);
        if (!imported.isEmpty()) {
            dictionaryImportRepository.deleteAllInBatch(imported);
        }

        // As a publisher: their listings. Other users' imports of those listings go with them via the
        // ON DELETE CASCADE foreign key
        // As a publisher (P4-13): their display name. deleteById is a no-op when there is none
        publisherRepository.deleteById(keycloakId);

        List<DictionaryStats> owned = dictionaryStatsRepository.findByUserId(keycloakId);
        if (owned.isEmpty()) {
            return;
        }

        // One DELETE ... WHERE dictionary_id IN (...), as in reconcile
        dictionaryStatsRepository.deleteAllInBatch(owned);
    }

    /**
     * One pass, one transaction: the snapshot is the complete set of public dictionaries at
     * `takenAt`, so whatever this service holds can be compared against it directly.
     * <p>
     * A dictionary made private in the seconds between the snapshot query and this method is safe:
     * its hidden row's `sourceUpdatedAt` is newer than the snapshot entry, so the entry is skipped.
     */
    @Transactional
    @Override
    public void reconcile(DictionarySnapshotEvent event) {
        OffsetDateTime takenAt = event.getTakenAt();
        List<DictionarySnapshotEntry> entries = Optional.ofNullable(event.getDictionaries()).orElse(List.of());

        Map<String, DictionaryStats> held = dictionaryStatsRepository.findAll().stream()
                .collect(Collectors.toMap(DictionaryStats::getDictionaryId, Function.identity()));

        List<DictionaryStats> toSave = new ArrayList<>();
        for (DictionarySnapshotEntry entry : entries) {
            OffsetDateTime sourceUpdatedAt = sourceUpdatedAt(entry.getUpdatedAt(), takenAt);
            DictionaryStats listing = held.get(entry.getDictionaryId());

            if (listing == null) {
                toSave.add(newRow(entry.getDictionaryId(), entry.getUserId(), entry.getDictionaryName(),
                        entry.getFromLang(), entry.getToLang(), entry.getTags(), sourceUpdatedAt, true));
            } else if (isNewer(sourceUpdatedAt, listing)) {
                applyPublicState(listing, entry.getUserId(), entry.getDictionaryName(), entry.getFromLang(),
                        entry.getToLang(), entry.getTags(), sourceUpdatedAt);
                toSave.add(listing);
            } else if (isMissingTagsOfThisVersion(entry, sourceUpdatedAt, listing)) {
                listing.setTags(normalizeTags(entry.getTags()));
                toSave.add(listing);
            }
        }

        Set<String> inSnapshot = entries.stream()
                .map(DictionarySnapshotEntry::getDictionaryId)
                .collect(Collectors.toSet());

        // Absent from the snapshot means "not public at takenAt" — but only for a row whose own state
        // predates takenAt. One made public a moment after the query is legitimately missing and must
        // survive until the next snapshot includes it.
        //
        // Hidden rows older than takenAt are removed too. A hidden row only has to outlive the public
        // state older than it that is still in flight, and after a snapshot taken later than the row
        // nothing that old is left in normal delivery. The one exception is a manual replay from the
        // DLQ, which could re-list a private dictionary until the next snapshot hides it again —
        // accepted, against keeping one row forever for every dictionary ever made private
        List<DictionaryStats> toRemove = held.values().stream()
                .filter(listing -> !inSnapshot.contains(listing.getDictionaryId()))
                .filter(listing -> listing.getSourceUpdatedAt().isBefore(takenAt))
                .toList();

        dictionaryStatsRepository.saveAllAndFlush(toSave);
        // deleteAllInBatch issues one DELETE ... WHERE dictionary_id IN (...). A derived deleteByXIn
        // would select the rows again and remove them one statement at a time
        if (!toRemove.isEmpty()) {
            dictionaryStatsRepository.deleteAllInBatch(toRemove);
        }

        // Anything non-zero here means an event was lost or overtaken since the last run — worth
        // seeing in the log, since it is the only signal that the event path has a leak. Removed
        // hidden rows are routine cleanup, not a leak
        log.info("dictionary.snapshot reconciled: {} public, {} created or corrected, {} removed",
                entries.size(), toSave.size(), toRemove.size());
    }

    private SliceResponse<DictionaryListingResponseDTO> browse(Specification<DictionaryStats> specification,
                                                               PageRequest pageRequest) {
        Slice<DictionaryStats> listings = dictionaryStatsRepository.findSlice(specification, pageRequest);

        // Display names for the whole page in one query (P4-13), not one per listing. Every listing
        // here has a named publisher — the query required it — so a miss only happens if the name was
        // cleared between the two queries; the listing then goes out without one
        Map<String, String> namesByPublisher = publisherRepository.findAllById(listings.stream()
                        .map(DictionaryStats::getUserId)
                        .distinct()
                        .toList()).stream()
                .filter(publisher -> publisher.getDisplayName() != null)
                .collect(Collectors.toMap(Publisher::getKeycloakId, Publisher::getDisplayName));

        return toSliceResponse(listings.map(listing -> {
            DictionaryListingResponseDTO dto = dictionaryStatsMapper.toDictionaryListingResponseDTO(listing);
            dto.setPublisherName(namesByPublisher.get(listing.getUserId()));
            return dto;
        }));
    }

    /**
     * Listed rows, narrowed by each filter the request set. Requested pairs are made canonical the same
     * way the stored ones are, so `TR-DE` and `de-tr` both match DE→TR and TR→DE listings; duplicates
     * collapse in the set. Tags likewise go through the same normalisation as stored ones.
     */
    private static Specification<DictionaryStats> toSpecification(ListingFilter filter) {
        // Listed, and published by someone with a display name (P4-13) — matching the name filter if set:
        // a case-insensitive substring, so "nna" finds "Anna Bauer"
        Specification<DictionaryStats> specification = isListed()
                .and(hasNamedPublisher(toContainsPattern(filter.publisherName())));

        if (filter.pairs() != null && !filter.pairs().isEmpty()) {
            Set<String> langPairs = filter.pairs().stream()
                    .map(LanguagePairUtils::toLangPair)
                    .collect(Collectors.toSet());
            specification = specification.and(hasLangPairIn(langPairs));
        }

        // Normalised like the stored tags, so "Food " matches "food"
        if (filter.tags() != null && !filter.tags().isEmpty()) {
            specification = specification.and(hasAnyTag(normalizeTags(filter.tags())));
        }

        return specification;
    }

    private static DictionaryStats newRow(String dictionaryId, String userId, String name, String fromLang,
                                          String toLang, List<String> tags, OffsetDateTime sourceUpdatedAt,
                                          boolean listed) {
        return DictionaryStats.builder()
                .dictionaryId(dictionaryId)
                .userId(userId)
                .name(name)
                .fromLang(normalizeLanguage(fromLang))
                .toLang(normalizeLanguage(toLang))
                .langPair(toLangPair(fromLang, toLang))
                // Unknown (null) tags start empty: the column is NOT NULL, and the next event or
                // snapshot that knows them fills them in
                .tags(tags == null ? new String[0] : normalizeTags(tags))
                // Both explicit — the column defaults do not apply through Hibernate (see the entity)
                .isListed(listed)
                .importCount(0)
                // Best available "went public" time: the change that made it public is the latest
                // change ms_dictionary reports for it. For a hidden row it is a placeholder, replaced
                // when the row is listed
                .publishedAt(sourceUpdatedAt)
                .sourceUpdatedAt(sourceUpdatedAt)
                .build();
    }

    /**
     * The newer state says "public": apply it, and re-list the row if it was hidden. A re-listed
     * row counts as newly published.
     */
    private static void applyPublicState(DictionaryStats listing, String userId, String name, String fromLang,
                                         String toLang, List<String> tags, OffsetDateTime sourceUpdatedAt) {
        if (!Boolean.TRUE.equals(listing.getIsListed())) {
            listing.setIsListed(true);
            listing.setPublishedAt(sourceUpdatedAt);
        }
        applyListingFields(listing, userId, name, fromLang, toLang, tags, sourceUpdatedAt);
    }

    private static void applyListingFields(DictionaryStats listing, String userId, String name, String fromLang,
                                           String toLang, List<String> tags, OffsetDateTime sourceUpdatedAt) {
        listing.setUserId(userId);
        listing.setName(name);
        listing.setFromLang(normalizeLanguage(fromLang));
        listing.setToLang(normalizeLanguage(toLang));
        listing.setLangPair(toLangPair(fromLang, toLang));
        // Null = a publisher or message from before P4-12, which does not know the tags: keep ours
        if (tags != null) {
            listing.setTags(normalizeTags(tags));
        }
        listing.setSourceUpdatedAt(sourceUpdatedAt);
    }

    /**
     * Tags as stored and as matched (P4-12): trimmed and lowercased with `Locale.ROOT` exactly like
     * ms_dictionary normalises them, without blanks or duplicates, sorted so equal sets compare equal.
     * ms_dictionary already sends them this way; doing it again here keeps the filter's matching
     * independent of the publisher getting it right.
     */
    private static String[] normalizeTags(List<String> tags) {
        return tags.stream()
                .filter(Objects::nonNull)
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .sorted()
                .toArray(String[]::new);
    }

    /**
     * The one exception to "only strictly newer state is applied" (rule 4), and only for tags. A
     * snapshot entry of the <i>same</i> version as the row describes the same dictionary state, so any
     * difference means the row is missing data — in practice, a listing stored before P4-12 added
     * tags, which no event would otherwise fill in (a tag change bumps `updatedAt`, but an untouched
     * dictionary never sends anything newer). Older entries stay ignored, as always.
     */
    private static boolean isMissingTagsOfThisVersion(DictionarySnapshotEntry entry, OffsetDateTime sourceUpdatedAt,
                                                      DictionaryStats listing) {
        return entry.getTags() != null
                && sourceUpdatedAt.isEqual(listing.getSourceUpdatedAt())
                && !Arrays.equals(normalizeTags(entry.getTags()), listing.getTags());
    }

    /**
     * Language codes are stored uppercase (decided at P4-06), and `lang_pair` is built from the same
     * uppercase codes, so the pair filter is a plain indexed equality (P4-11). ms_dictionary validates case-insensitively but stores codes exactly as the
     * client sent them, so events arrive as `en` as often as `EN`. Locale.ROOT, not the default
     * locale: TR is a supported language, and Turkish uppercasing turns `i` into `İ`.
     */
    private static String normalizeLanguage(String language) {
        return language == null ? null : language.toUpperCase(Locale.ROOT);
    }

    /**
     * Rule 4: only strictly newer state is applied. Equal counts as stale, so the same event
     * delivered twice is a no-op.
     * <p>
     * Postgres keeps microseconds while an event can carry nanoseconds, so a redelivery may compare as
     * a hair newer than the stored value and re-apply identical fields. Harmless — same values —
     * and not worth a rounding rule that would have to match Postgres's exactly.
     */
    private static boolean isNewer(OffsetDateTime incoming, DictionaryStats listing) {
        return incoming.isAfter(listing.getSourceUpdatedAt());
    }

    /**
     * `update_dt` is nullable in ms_dictionary, so a row that predates it can arrive without an
     * `updatedAt`. The fallback keeps `source_updated_at` NOT NULL; the cost is at most one redundant
     * re-apply of identical values on a later delivery.
     */
    private static OffsetDateTime sourceUpdatedAt(OffsetDateTime updatedAt, OffsetDateTime fallback) {
        return Objects.requireNonNullElse(updatedAt, fallback);
    }
}
