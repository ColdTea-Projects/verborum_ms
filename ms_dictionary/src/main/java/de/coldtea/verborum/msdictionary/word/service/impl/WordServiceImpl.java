package de.coldtea.verborum.msdictionary.word.service.impl;

import de.coldtea.verborum.msdictionary.common.event.OutboundEvent;
import de.coldtea.verborum.msdictionary.common.event.WordCreatedEvent;
import de.coldtea.verborum.msdictionary.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msdictionary.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msdictionary.common.mapper.WordMapper;
import de.coldtea.verborum.msdictionary.common.utils.ListUtils;
import de.coldtea.verborum.msdictionary.dictionary.entity.Dictionary;
import de.coldtea.verborum.msdictionary.dictionary.repository.DictionaryRepository;
import de.coldtea.verborum.msdictionary.word.dto.WordBundleRequestDTO;
import de.coldtea.verborum.msdictionary.word.entity.Word;
import de.coldtea.verborum.msdictionary.word.repository.WordRepository;
import de.coldtea.verborum.msdictionary.word.dto.WordResponseDTO;
import de.coldtea.verborum.msdictionary.word.service.WordService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_WORD_CREATED;
import static de.coldtea.verborum.msdictionary.common.constants.ErrorMessageConstants.DICTIONARY_WAS_NOT_FOUND_ID;
import static de.coldtea.verborum.msdictionary.common.constants.ErrorMessageConstants.NOT_THE_OWNER;
import static de.coldtea.verborum.msdictionary.common.constants.ErrorMessageConstants.WORD_IN_ANOTHER_DICTIONARY;
import static de.coldtea.verborum.msdictionary.common.utils.DictionaryAccessUtils.isReadableBy;

@Service
@RequiredArgsConstructor
public class WordServiceImpl implements WordService {

    private final WordRepository wordRepository;

    private final DictionaryRepository dictionaryRepository;
    private final WordMapper wordMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ListUtils listUtils = new ListUtils();

    @Transactional
    @Override
    public void saveWords(List<WordBundleRequestDTO> bundles, String ownerId) {
        List<Word> words = listUtils.flatMap(bundles, bundle -> convertToWordStream(bundle, ownerId));

        // saveWords() backs both POST and PUT, so work out which ids are genuinely new before the
        // save overwrites the evidence. Re-announcing an edited word as created would have
        // ms_autofil count the same translation twice (see P1-05 in roadmap.md)
        Map<String, Word> alreadyStored = wordRepository.findAllById(words.stream().map(Word::getWordId).toList())
                .stream()
                .collect(Collectors.toMap(Word::getWordId, Function.identity()));

        // SEC-01: convertToWordStream() proved the caller owns every *target* dictionary, but a wordId
        // that already exists elsewhere would be upserted out of its own dictionary into the caller's —
        // a takeover of someone else's word (public word ids are readable by every member since P4-10).
        // An existing word must stay in the dictionary it is in; since that dictionary is the owned
        // target, this is also the ownership check. Refused before anything is written
        words.stream()
                .filter(word -> alreadyStored.containsKey(word.getWordId()))
                .filter(word -> !alreadyStored.get(word.getWordId()).getDictionaryId().equals(word.getDictionaryId()))
                .findAny()
                .ifPresent(word -> {
                    throw new ForbiddenOperationException(WORD_IN_ANOTHER_DICTIONARY);
                });

        wordRepository.saveAllAndFlush(words);

        // Raising these only queues them; OutboundEventPublisher sends after commit (rule 1), so an
        // exception later in this method can no longer leave events announcing words that were
        // rolled back
        publishWordCreatedEvents(words.stream()
                .filter(word -> !alreadyStored.containsKey(word.getWordId()))
                .toList());
    }

    private void publishWordCreatedEvents(List<Word> createdWords) {
        if (createdWords.isEmpty()) {
            return;
        }

        // userId/fromLang/toLang live on Dictionary, not Word. One batched query over the distinct
        // dictionary ids in this batch rather than one per word (findAllById runs a real IN query
        // — it does not read through the persistence context)
        Map<String, Dictionary> dictionariesById = dictionaryRepository.findAllById(
                        createdWords.stream().map(Word::getDictionaryId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Dictionary::getDictionaryId, Function.identity()));

        createdWords.forEach(word -> {
            // convertToWordStream() already threw for any unknown dictionary, so this is present
            // unless the row was deleted concurrently mid-transaction. Fail loudly rather than
            // NPE on the builder below
            Dictionary dictionary = dictionariesById.get(word.getDictionaryId());
            if (dictionary == null) {
                throw new RecordNotFoundException(DICTIONARY_WAS_NOT_FOUND_ID + word.getDictionaryId());
            }

            eventPublisher.publishEvent(new OutboundEvent(
                    ROUTING_KEY_WORD_CREATED,
                    WordCreatedEvent.builder()
                            .wordId(word.getWordId())
                            .dictionaryId(word.getDictionaryId())
                            .userId(dictionary.getUserId())
                            .word(word.getWord())
                            .translation(word.getTranslation())
                            .fromLang(dictionary.getFromLang())
                            .toLang(dictionary.getToLang())
                            .eventTimestamp(OffsetDateTime.now())
                            .build()));
        });
    }

    @Transactional
    @Override
    public void deleteWords(List<String> wordIdList, String ownerId) {
        // A word carries no owner of its own, so resolve through its dictionary. Anything the caller
        // does not own is dropped from the delete rather than refused — a 403 would confirm the id
        // exists (P3-08)
        List<String> ownedWordIds = wordRepository.findAllById(wordIdList).stream()
                .filter(word -> ownsDictionary(word.getDictionaryId(), ownerId))
                .map(Word::getWordId)
                .toList();

        if (ownedWordIds.isEmpty()) {
            return;
        }

        wordRepository.deleteAllById(ownedWordIds);
    }

    @Override
    @Transactional
    public void deleteWordsByDictionaryId(String dictionaryId, String ownerId) {
        Dictionary dictionary = dictionaryRepository.findById(dictionaryId)
                .orElseThrow(() -> new RecordNotFoundException(DICTIONARY_WAS_NOT_FOUND_ID + dictionaryId));

        if (!ownerId.equals(dictionary.getUserId())) {
            throw new ForbiddenOperationException(NOT_THE_OWNER);
        }

        wordRepository.deleteWordsByDictionaryId(dictionaryId);
    }

    @Override
    public List<WordResponseDTO> getWordsByLanguageFrom(String language, String ownerId) {
        // Scoped to the caller: this used to return every user's words for the language, which was
        // a straight cross-user read (P3-08)
        List<String> dictIds = dictionaryRepository.findByFromLang(language).stream()
                .filter(dictionary -> ownerId.equals(dictionary.getUserId()))
                .map(Dictionary::getDictionaryId)
                .toList();
        List<Word> words = wordRepository.findByDictionaryIdIn(dictIds);
        return words.stream().map(wordMapper::toWordResponseDTO).toList();
    }

    @Override
    public List<WordResponseDTO> getWordsByLanguageTo(String language, String ownerId) {
        List<String> dictIds = dictionaryRepository.findByToLang(language).stream()
                .filter(dictionary -> ownerId.equals(dictionary.getUserId()))
                .map(Dictionary::getDictionaryId)
                .toList();
        List<Word> words = wordRepository.findByDictionaryIdIn(dictIds);
        return words.stream().map(wordMapper::toWordResponseDTO).toList();
    }

    private boolean ownsDictionary(String dictionaryId, String ownerId) {
        return dictionaryRepository.findById(dictionaryId)
                .map(dictionary -> ownerId.equals(dictionary.getUserId()))
                .orElse(false);
    }

    @Override
    public List<WordResponseDTO> getWordsByUserId(String userId) {
        List<String> dictIds = dictionaryRepository.findByUserId(userId).stream().map(Dictionary::getDictionaryId).toList();
        List<Word> words = wordRepository.findByDictionaryIdIn(dictIds);

        return words.stream().map(wordMapper::toWordResponseDTO).toList();
    }

    /**
     * Words of every dictionary the caller may read: their own and public ones (P4-10) — how an
     * imported marketplace dictionary is opened. Unreadable ids are dropped, not refused (P3-08).
     */
    @Override
    public List<WordResponseDTO> getWordsByDictionaryIds(List<String> dictionaryIds, String ownerId) {
        List<Dictionary> readable = dictionaryRepository.findAllById(dictionaryIds).stream()
                .filter(dictionary -> isReadableBy(dictionary, ownerId))
                .toList();

        if (readable.isEmpty()) {
            return List.of();
        }

        Set<String> ownedIds = ownedDictionaryIds(readable, ownerId);
        List<String> readableIds = readable.stream().map(Dictionary::getDictionaryId).toList();

        return wordRepository.findByDictionaryIdIn(readableIds).stream()
                .map(word -> toResponseForCaller(word, ownedIds))
                .toList();
    }

    /** Batch by word id, filtered to words in dictionaries the caller may read (P4-10). */
    @Override
    public List<WordResponseDTO> getWordsByIds(List<String> wordIds, String ownerId) {
        List<Word> words = wordRepository.findAllById(wordIds);
        if (words.isEmpty()) {
            return List.of();
        }

        List<String> dictionaryIds = words.stream().map(Word::getDictionaryId).distinct().toList();
        List<Dictionary> readable = dictionaryRepository.findAllById(dictionaryIds).stream()
                .filter(dictionary -> isReadableBy(dictionary, ownerId))
                .toList();
        Set<String> readableIds = readable.stream().map(Dictionary::getDictionaryId).collect(Collectors.toSet());
        Set<String> ownedIds = ownedDictionaryIds(readable, ownerId);

        return words.stream()
                .filter(word -> readableIds.contains(word.getDictionaryId()))
                .map(word -> toResponseForCaller(word, ownedIds))
                .toList();
    }

    private static Set<String> ownedDictionaryIds(List<Dictionary> dictionaries, String ownerId) {
        return dictionaries.stream()
                .filter(dictionary -> ownerId.equals(dictionary.getUserId()))
                .map(Dictionary::getDictionaryId)
                .collect(Collectors.toSet());
    }

    /**
     * `level` is the owner's personal mastery of the word. Another reader of a public dictionary gets
     * it as null: it is someone else's learning progress, and an importer's client would otherwise show
     * it as their own. Null already means "not provided" in this contract.
     */
    private WordResponseDTO toResponseForCaller(Word word, Set<String> ownedDictionaryIds) {
        WordResponseDTO response = wordMapper.toWordResponseDTO(word);
        if (!ownedDictionaryIds.contains(word.getDictionaryId())) {
            response.setLevel(null);
        }
        return response;
    }

    private Stream<Word> convertToWordStream(@NotNull WordBundleRequestDTO bundle, String ownerId){
        Dictionary dictionary = dictionaryRepository.findById(bundle.getDictionaryId())
                .orElseThrow(() -> new RecordNotFoundException(DICTIONARY_WAS_NOT_FOUND_ID + bundle.getDictionaryId()));

        // P3-05: a word inherits its owner from its dictionary, so writing into a dictionary that is
        // not yours is the same hole as claiming another userId outright
        if (!ownerId.equals(dictionary.getUserId())) {
            throw new ForbiddenOperationException(NOT_THE_OWNER);
        }

        return listUtils.map(bundle.getWords(),
                word -> wordMapper.toWord(bundle.getDictionaryId(), word)
        ).stream();
    }
}
