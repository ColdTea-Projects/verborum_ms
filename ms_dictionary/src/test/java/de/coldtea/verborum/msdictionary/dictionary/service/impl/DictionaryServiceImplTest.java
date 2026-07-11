package de.coldtea.verborum.msdictionary.dictionary.service.impl;

import de.coldtea.verborum.msdictionary.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msdictionary.common.mapper.DictionaryMapper;
import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryRequestDTO;
import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryResponseDTO;
import de.coldtea.verborum.msdictionary.dictionary.entity.Dictionary;
import de.coldtea.verborum.msdictionary.dictionary.repository.DictionaryRepository;
import de.coldtea.verborum.msdictionary.word.repository.WordRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DictionaryServiceImplTest {

    @Mock
    private DictionaryRepository dictionaryRepository;

    @Mock
    private WordRepository wordRepository;

    @Mock
    private DictionaryMapper dictionaryMapper;

    @InjectMocks
    private DictionaryServiceImpl dictionaryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }



    @Test
    void saveDictionary_Success() {
        // Arrange
        DictionaryRequestDTO requestDTO = new DictionaryRequestDTO();
        Dictionary dictionary = new Dictionary();
        DictionaryResponseDTO responseDTO = new DictionaryResponseDTO();

        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(dictionary);
        when(dictionaryRepository.saveAndFlush(dictionary)).thenReturn(dictionary);
        when(dictionaryMapper.toDictionaryResponseDTO(dictionary)).thenReturn(responseDTO);

        // Act
        DictionaryResponseDTO result = dictionaryService.saveDictionary(requestDTO);

        // Assert
        assertEquals(responseDTO, result);
        verify(dictionaryMapper).toDictionary(requestDTO);
        verify(dictionaryRepository).saveAndFlush(dictionary);
        verify(dictionaryMapper).toDictionaryResponseDTO(dictionary);
    }

    @Test
    void saveDictionary_Failure() {
        // Arrange
        DictionaryRequestDTO requestDTO = new DictionaryRequestDTO();
        Dictionary dictionary = new Dictionary();

        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(dictionary);
        when(dictionaryRepository.saveAndFlush(dictionary)).thenThrow(new RuntimeException("Unable to save dictionary"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> dictionaryService.saveDictionary(requestDTO));
        verify(dictionaryMapper).toDictionary(requestDTO);
        verify(dictionaryRepository).saveAndFlush(dictionary);
        verifyNoMoreInteractions(dictionaryMapper);
    }

    @Test
    void deleteDictionary_Success() {
        // Arrange
        String dictionaryId = "1";

        // Act
        dictionaryService.deleteDictionary(dictionaryId);

        // Assert
        verify(wordRepository).deleteByDictionaryIdIn(List.of(dictionaryId));
        verify(dictionaryRepository).deleteById(dictionaryId);
    }

    @Test
    void getDictionaryById_Success() {
        // Arrange
        String dictionaryId = "1";
        Dictionary dictionary = new Dictionary();
        DictionaryResponseDTO responseDTO = new DictionaryResponseDTO();

        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.of(dictionary));
        when(dictionaryMapper.toDictionaryResponseDTO(dictionary)).thenReturn(responseDTO);

        // Act
        DictionaryResponseDTO result = dictionaryService.getDictionaryById(dictionaryId);

        // Assert
        assertEquals(responseDTO, result);
        verify(dictionaryRepository).findById(dictionaryId);
        verify(dictionaryMapper).toDictionaryResponseDTO(dictionary);
    }

    @Test
    void getDictionaryById_NotFound() {
        // Arrange
        String dictionaryId = "1";
        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RecordNotFoundException.class, () -> dictionaryService.getDictionaryById(dictionaryId));
        verifyNoInteractions(dictionaryMapper);
    }

    @Test
    void getDictionariesByIds_Success() {
        // Arrange
        List<String> dictionaryIds = List.of("1", "2");
        List<Dictionary> dictionaries = Arrays.asList(new Dictionary(), new Dictionary());

        when(dictionaryRepository.findAllById(dictionaryIds)).thenReturn(dictionaries);
        when(dictionaryMapper.toDictionaryResponseDTO(any(Dictionary.class))).thenReturn(new DictionaryResponseDTO());

        // Act
        List<DictionaryResponseDTO> result = dictionaryService.getDictionariesByIds(dictionaryIds);

        // Assert
        assertEquals(dictionaries.size(), result.size());
        verify(dictionaryRepository).findAllById(dictionaryIds);
        verify(dictionaryMapper, times(dictionaries.size())).toDictionaryResponseDTO(any(Dictionary.class));
    }

    @Test
    void getDictionariesByIds_NoneFound() {
        // Arrange
        List<String> dictionaryIds = List.of("1", "2");
        when(dictionaryRepository.findAllById(dictionaryIds)).thenReturn(List.of());

        // Act
        List<DictionaryResponseDTO> result = dictionaryService.getDictionariesByIds(dictionaryIds);

        // Assert
        assertEquals(0, result.size());
        verify(dictionaryRepository).findAllById(dictionaryIds);
        verifyNoInteractions(dictionaryMapper);
    }

    @Test
    void getDictionariesByUser_Success() {
        // Arrange
        String userId = "user1";
        List<Dictionary> dictionaries = Arrays.asList(new Dictionary(), new Dictionary());
        List<DictionaryResponseDTO> expectedResponse = Arrays.asList(new DictionaryResponseDTO(), new DictionaryResponseDTO());

        when(dictionaryRepository.findByUserId(userId)).thenReturn(dictionaries);
        when(dictionaryMapper.toDictionaryResponseDTO(any(Dictionary.class))).thenReturn(new DictionaryResponseDTO());

        // Act
        List<DictionaryResponseDTO> result = dictionaryService.getDictionariesByUser(userId);

        // Assert
        assertEquals(expectedResponse.size(), result.size());
        verify(dictionaryRepository).findByUserId(userId);
        verify(dictionaryMapper, times(dictionaries.size())).toDictionaryResponseDTO(any(Dictionary.class));
    }}
