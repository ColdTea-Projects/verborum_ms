package de.coldtea.verborum.msdictionary.tag.service.impl;

import de.coldtea.verborum.msdictionary.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msdictionary.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msdictionary.common.mapper.DictionaryTagMapper;
import de.coldtea.verborum.msdictionary.dictionary.entity.Dictionary;
import de.coldtea.verborum.msdictionary.dictionary.repository.DictionaryRepository;
import de.coldtea.verborum.msdictionary.dictionary.service.DictionaryService;
import de.coldtea.verborum.msdictionary.tag.dto.DictionaryTagRequestDTO;
import de.coldtea.verborum.msdictionary.tag.dto.DictionaryTagResponseDTO;
import de.coldtea.verborum.msdictionary.tag.entity.DictionaryTag;
import de.coldtea.verborum.msdictionary.tag.repository.DictionaryTagRepository;
import de.coldtea.verborum.msdictionary.tag.service.DictionaryTagService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static de.coldtea.verborum.msdictionary.common.constants.ErrorMessageConstants.DICTIONARY_WAS_NOT_FOUND_ID;
import static de.coldtea.verborum.msdictionary.common.constants.ErrorMessageConstants.NOT_THE_OWNER;
import static de.coldtea.verborum.msdictionary.common.utils.DictionaryAccessUtils.isReadableBy;

@Service
@RequiredArgsConstructor
public class DictionaryTagServiceImpl implements DictionaryTagService {

    private final DictionaryTagRepository dictionaryTagRepository;

    private final DictionaryRepository dictionaryRepository;

    private final DictionaryTagMapper dictionaryTagMapper;

    // Tags are listed on the marketplace (P4-12): a real change on a public dictionary must reach it
    private final DictionaryService dictionaryService;

    @Override
    public List<DictionaryTagResponseDTO> getTagsByDictionary(String dictionaryId, String ownerId) {
        // 404 rather than 403 for a dictionary the caller does not own — same rule as
        // getDictionaryById, so the response cannot be used to probe which ids exist
        requireReadableDictionary(dictionaryId, ownerId);

        return dictionaryTagRepository.findByDictionaryId(dictionaryId).stream()
                .map(dictionaryTagMapper::toDictionaryTagResponseDTO)
                .toList();
    }

    @Transactional
    @Override
    public DictionaryTagResponseDTO addTag(String dictionaryId, DictionaryTagRequestDTO tagRequestDTO, String ownerId) {
        requireOwnedDictionary(dictionaryId, ownerId);

        String tag = normalise(tagRequestDTO.getTag());

        // Idempotent, like the vault: a dictionary's tags are a set, enforced by
        // UNIQUE (fk_dictionary_id, tag). Re-adding returns the existing row instead of failing,
        // which also means a retrying client cannot produce duplicates
        return dictionaryTagRepository.findByDictionaryIdAndTag(dictionaryId, tag)
                .map(dictionaryTagMapper::toDictionaryTagResponseDTO)
                .orElseGet(() -> createTag(dictionaryId, tag));
    }

    @Transactional
    @Override
    public void deleteTag(String dictionaryId, String tag, String ownerId) {
        requireOwnedDictionary(dictionaryId, ownerId);

        // Removing a tag that is not there is a silent no-op, matching deleteDictionary/deleteWords —
        // and announces nothing
        if (dictionaryTagRepository.deleteByDictionaryIdAndTag(dictionaryId, normalise(tag)) > 0) {
            dictionaryService.publishTagChange(dictionaryId);
        }
    }

    private DictionaryTagResponseDTO createTag(String dictionaryId, String tag) {
        DictionaryTag dictionaryTag = DictionaryTag.builder()
                .tagId(UUID.randomUUID().toString())
                .dictionaryId(dictionaryId)
                .tag(tag)
                .build();

        DictionaryTag saved = dictionaryTagRepository.saveAndFlush(dictionaryTag);

        // Only here, on a real insert — re-adding an existing tag returns before this and announces
        // nothing
        dictionaryService.publishTagChange(dictionaryId);

        return dictionaryTagMapper.toDictionaryTagResponseDTO(saved);
    }

    /**
     * Tags are grouping keys, not display text: the marketplace will browse by them and the AI work
     * will aggregate over them, so "Food", "food " and "FOOD" must be one tag rather than three.
     * Trimmed and lower-cased on the way in — a client that wants a pretty label should render it
     * itself. Applied on delete too, so removing "Food" removes what "food" stored.
     */
    private static String normalise(String tag) {
        return tag.trim().toLowerCase(Locale.ROOT);
    }

    /** Writes: a dictionary owned by someone else is refused outright. */
    private void requireOwnedDictionary(String dictionaryId, String ownerId) {
        Dictionary dictionary = dictionaryRepository.findById(dictionaryId)
                .orElseThrow(() -> new RecordNotFoundException(DICTIONARY_WAS_NOT_FOUND_ID + dictionaryId));

        if (!ownerId.equals(dictionary.getUserId())) {
            throw new ForbiddenOperationException(NOT_THE_OWNER);
        }
    }

    /** Reads: someone else's dictionary is indistinguishable from one that does not exist. */
    private void requireReadableDictionary(String dictionaryId, String ownerId) {
        Dictionary dictionary = dictionaryRepository.findById(dictionaryId)
                .orElseThrow(() -> new RecordNotFoundException(DICTIONARY_WAS_NOT_FOUND_ID + dictionaryId));

        // Owned or public (P4-10) — a public dictionary's tags are part of what the marketplace shows
        if (!isReadableBy(dictionary, ownerId)) {
            throw new RecordNotFoundException(DICTIONARY_WAS_NOT_FOUND_ID + dictionaryId);
        }
    }
}
