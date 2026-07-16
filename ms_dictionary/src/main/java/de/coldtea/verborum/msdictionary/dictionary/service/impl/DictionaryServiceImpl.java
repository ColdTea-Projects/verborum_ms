package de.coldtea.verborum.msdictionary.dictionary.service.impl;

import de.coldtea.verborum.msdictionary.common.event.DictionaryDeletedEvent;
import de.coldtea.verborum.msdictionary.common.event.DictionaryVisibilityEvent;
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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.EXCHANGE;
import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_DELETED;
import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_VISIBILITY_PRIVATE;
import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC;
import static de.coldtea.verborum.msdictionary.common.constants.ErrorMessageConstants.DICTIONARY_WAS_NOT_FOUND_ID;

@Service
@RequiredArgsConstructor
public class DictionaryServiceImpl implements DictionaryService {

    private final DictionaryRepository dictionaryRepository;

    private final WordRepository wordRepository;

    private final DictionaryMapper dictionaryMapper;

    private final RabbitTemplate rabbitTemplate;


    @Transactional
    @Override
    public DictionaryResponseDTO saveDictionary(DictionaryRequestDTO dictionaryRequestDTO) {
        // Read the current visibility before saving over it — a dictionary that is absent has
        // never been public, so it counts as private
        boolean wasPublic = dictionaryRepository.findById(dictionaryRequestDTO.getDictionaryId())
                .map(existing -> Boolean.TRUE.equals(existing.getIsPublic()))
                .orElse(false);

        Dictionary savedDictionary = dictionaryRepository.saveAndFlush(dictionaryMapper.toDictionary(dictionaryRequestDTO));

        // Map before publishing so the send is the last thing that can happen in the transaction:
        // RabbitTemplate is not transactional, so anything that throws after it would roll back
        // the write while the event stays published
        DictionaryResponseDTO responseDTO = dictionaryMapper.toDictionaryResponseDTO(savedDictionary);

        publishVisibilityChange(savedDictionary, wasPublic);

        return responseDTO;
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

        rabbitTemplate.convertAndSend(
                EXCHANGE,
                isPublic ? ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC : ROUTING_KEY_DICTIONARY_VISIBILITY_PRIVATE,
                DictionaryVisibilityEvent.builder()
                        .dictionaryId(dictionary.getDictionaryId())
                        .userId(dictionary.getUserId())
                        .isPublic(isPublic)
                        .fromLang(dictionary.getFromLang())
                        .toLang(dictionary.getToLang())
                        .dictionaryName(dictionary.getName())
                        .eventTimestamp(LocalDateTime.now())
                        .build()
        );
    }

    @Transactional
    @Override
    public void deleteDictionary(String dictionaryId) {
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

        rabbitTemplate.convertAndSend(
                EXCHANGE,
                ROUTING_KEY_DICTIONARY_DELETED,
                DictionaryDeletedEvent.builder()
                        .dictionaryId(dictionary.getDictionaryId())
                        .userId(dictionary.getUserId())
                        .eventTimestamp(LocalDateTime.now())
                        .build()
        );
    }

    @Override
    public List<DictionaryResponseDTO> getDictionariesByUser(String userId) {
        return dictionaryRepository.findByUserId(userId).stream().map(dictionaryMapper::toDictionaryResponseDTO).toList();
    }

    @Override
    public DictionaryResponseDTO getDictionaryById(String dictionaryId) {
        Dictionary dictionary = dictionaryRepository.findById(dictionaryId)
                .orElseThrow(() -> new RecordNotFoundException(DICTIONARY_WAS_NOT_FOUND_ID + dictionaryId));
        return dictionaryMapper.toDictionaryResponseDTO(dictionary);
    }

    @Override
    public List<DictionaryResponseDTO> getDictionariesByIds(List<String> dictionaryIds) {
        return dictionaryRepository.findAllById(dictionaryIds).stream().map(dictionaryMapper::toDictionaryResponseDTO).toList();
    }

}
