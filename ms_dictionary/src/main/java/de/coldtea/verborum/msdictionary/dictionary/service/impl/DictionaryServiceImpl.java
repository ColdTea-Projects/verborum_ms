package de.coldtea.verborum.msdictionary.dictionary.service.impl;

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
import org.springframework.stereotype.Service;

import java.util.List;

import static de.coldtea.verborum.msdictionary.common.constants.ErrorMessageConstants.DICTIONARY_WAS_NOT_FOUND_ID;

@Service
@RequiredArgsConstructor
public class DictionaryServiceImpl implements DictionaryService {

    private final DictionaryRepository dictionaryRepository;

    private final WordRepository wordRepository;

    private final DictionaryMapper dictionaryMapper;


    @Transactional
    @Override
    public DictionaryResponseDTO saveDictionary(DictionaryRequestDTO dictionaryRequestDTO) {
        Dictionary savedDictionary = dictionaryRepository.saveAndFlush(dictionaryMapper.toDictionary(dictionaryRequestDTO));
        return dictionaryMapper.toDictionaryResponseDTO(savedDictionary);
    }

    @Transactional
    @Override
    public void deleteDictionary(String dictionaryId) {
        // Words reference the dictionary without a DB-level FK — delete them explicitly
        wordRepository.deleteByDictionaryIdIn(List.of(dictionaryId));
        dictionaryRepository.deleteById(dictionaryId);
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
