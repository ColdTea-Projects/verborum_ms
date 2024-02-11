package de.coldtea.verborum.msdictionary.dictionary.service;

import de.coldtea.verborum.msdictionary.common.utils.ListUtils;
import de.coldtea.verborum.msdictionary.dictionary.repository.entity.Dictionary;
import de.coldtea.verborum.msdictionary.dictionary.repository.DictionaryRepository;
import de.coldtea.verborum.msdictionary.dictionary.service.dto.DictionaryRequestDTO;
import de.coldtea.verborum.msdictionary.dictionary.service.dto.DictionaryResponseDTO;
import de.coldtea.verborum.msdictionary.word.repository.entity.Word;
import de.coldtea.verborum.msdictionary.word.service.dto.WordResponseDTO;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DictionaryService {

    private final DictionaryRepository dictionaryRepository;

    private final ListUtils listUtils = new ListUtils();

    @Transactional
    public void saveDictionary(DictionaryRequestDTO dictionaryDto) {
        Dictionary dictionary = new Dictionary(
                dictionaryDto.getDictionaryId(),
                dictionaryDto.getUserId(),
                dictionaryDto.getName(),
                dictionaryDto.getIsPublic(),
                dictionaryDto.getFromLang(),
                dictionaryDto.getToLang()
        );

        dictionaryRepository.saveAndFlush(dictionary);
    }

    @Transactional
    public void deleteDictionary(String dictionaryId) {
        dictionaryRepository.deleteById(dictionaryId);
    }

    public List<DictionaryResponseDTO> getDictionariesByUser(String userId) {
        return listUtils.map(dictionaryRepository.findByUserId(userId), this::convertToDTO);
    }

    public List<DictionaryResponseDTO> getDictionariesById(List<String> dictionaryId) {
        return listUtils.map(dictionaryRepository.findAllById(dictionaryId), this::convertToDTO);
    }

    public List<DictionaryResponseDTO> getDictionaries() {
        return listUtils.map(dictionaryRepository.findAll(), this::convertToDTO);
    }

    private DictionaryResponseDTO convertToDTO(Dictionary dictionary) {
        return new DictionaryResponseDTO(
                dictionary.getDictionaryId(),
                dictionary.getUserId(),
                dictionary.getName(),
                dictionary.getIsPublic(),
                dictionary.getFromLang(),
                dictionary.getToLang()
        );
    }
}
