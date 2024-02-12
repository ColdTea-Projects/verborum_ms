package de.coldtea.verborum.msdictionary.dictionary.service.impl;

import de.coldtea.verborum.msdictionary.common.mapper.DictionaryMapper;
import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryRequestDTO;
import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryResponseDTO;
import de.coldtea.verborum.msdictionary.dictionary.repository.DictionaryRepository;
import de.coldtea.verborum.msdictionary.dictionary.service.DictionaryService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DictionaryServiceImpl implements DictionaryService {

    private final DictionaryRepository dictionaryRepository;

    private final DictionaryMapper dictionaryMapper;


    @Transactional
    @Override
    public void saveDictionary(DictionaryRequestDTO dictionaryRequestDTO) {
        dictionaryRepository.saveAndFlush(dictionaryMapper.toDictionary(dictionaryRequestDTO));
    }

    @Transactional
    @Override
    public void deleteDictionary(String dictionaryId) {
        dictionaryRepository.deleteById(dictionaryId);
    }

    @Override
    public List<DictionaryResponseDTO> getDictionariesByUser(String userId) {
        return dictionaryRepository.findByUserId(userId).stream().map(dictionaryMapper::toDictionaryResponseDTO).toList();
    }

}
