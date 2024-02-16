package de.coldtea.verborum.msdictionary.word.service.impl;

import de.coldtea.verborum.msdictionary.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msdictionary.common.mapper.WordMapper;
import de.coldtea.verborum.msdictionary.dictionary.entity.Dictionary;
import de.coldtea.verborum.msdictionary.dictionary.repository.DictionaryRepository;
import de.coldtea.verborum.msdictionary.word.dto.WordResponseDTO;
import de.coldtea.verborum.msdictionary.word.entity.Word;
import de.coldtea.verborum.msdictionary.word.repository.WordRepository;
import de.coldtea.verborum.msdictionary.word.dto.WordRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WordServiceImplTest {

    @Mock
    private WordRepository wordRepository;

    @Mock
    private DictionaryRepository dictionaryRepository;

    @Mock
    private WordMapper wordMapper;

    @InjectMocks
    private WordServiceImpl wordService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void saveWords_Success() {
        // Arrange
        String dictionaryId = "1";
        List<WordRequestDTO> wordList = new ArrayList<>();
        wordList.add(new WordRequestDTO());

        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.of(new Dictionary()));
        when(wordMapper.toWord(any(WordRequestDTO.class))).thenReturn(new Word());

        // Act
        assertDoesNotThrow(() -> wordService.saveWords(dictionaryId, wordList));

        // Assert
        verify(dictionaryRepository).findById(dictionaryId);
        verify(wordRepository).saveAllAndFlush(any());
    }

    @Test
    void saveWords_DictionaryNotFound() {
        // Arrange
        String dictionaryId = "1";
        List<WordRequestDTO> wordList = new ArrayList<>();

        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RecordNotFoundException.class, () -> wordService.saveWords(dictionaryId, wordList));
    }

    @Test
    void deleteWords_Success() {
        // Arrange
        List<String> wordIdList = new ArrayList<>();

        // Act
        assertDoesNotThrow(() -> wordService.deleteWords(wordIdList));

        // Assert
        verify(wordRepository).deleteAllById(wordIdList);
    }


    @Test
    void deleteWordsByDictionaryId_Success() {
        // Arrange
        String dictionaryId = "1";
        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.of(new Dictionary()));

        // Act
        assertDoesNotThrow(() -> wordService.deleteWordsByDictionaryId(dictionaryId));

        // Assert
        verify(wordRepository).deleteWordsByDictionaryId(dictionaryId);
    }

    @Test
    void deleteWordsByDictionaryId_DictionaryNotFound() {
        // Arrange
        String dictionaryId = "1";
        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RecordNotFoundException.class, () -> wordService.deleteWordsByDictionaryId(dictionaryId));
    }


    @Test
    void getWordsByLanguageFrom_Success() {
        // Arrange
        String language = "EN";
        List<String> dictIds = List.of("1", "2");
        List<Word> words = new ArrayList<>();
        words.add(new Word());

        Dictionary d1 = Dictionary.builder().dictionaryId("1")
                .fromLang(language)
                .build();

        Dictionary d2 = Dictionary.builder().dictionaryId("2")
                .fromLang(language)
                .build();
        when(dictionaryRepository.findByFromLang(language)).thenReturn(List.of(d1, d2));
        when(wordRepository.findByDictionaryIdIn(dictIds)).thenReturn(words);
        when(wordMapper.toWordResponseDTO(any(Word.class))).thenReturn(new WordResponseDTO());

        // Act
        List<WordResponseDTO> result = wordService.getWordsByLanguageFrom(language);

        // Assert
        assertFalse(result.isEmpty());
        verify(dictionaryRepository).findByFromLang(language);
        verify(wordRepository).findByDictionaryIdIn(dictIds);
        verify(wordMapper, times(words.size())).toWordResponseDTO(any(Word.class));
    }

    @Test
    void getWordsByLanguageTo_Success() {
        // Arrange
        String language = "EN";
        List<String> dictIds = List.of("1", "2");
        List<Word> words = new ArrayList<>();
        words.add(new Word());

        Dictionary d1 = Dictionary.builder().dictionaryId("1")
                .fromLang(language)
                .build();

        Dictionary d2 = Dictionary.builder().dictionaryId("2")
                .fromLang(language)
                .build();
        when(dictionaryRepository.findByToLang(language)).thenReturn(List.of(d1, d2));
        when(wordRepository.findByDictionaryIdIn(dictIds)).thenReturn(words);
        when(wordMapper.toWordResponseDTO(any(Word.class))).thenReturn(new WordResponseDTO());

        // Act
        List<WordResponseDTO> result = wordService.getWordsByLanguageTo(language);

        // Assert
        assertFalse(result.isEmpty());
        verify(dictionaryRepository).findByToLang(language);
        verify(wordRepository).findByDictionaryIdIn(dictIds);
        verify(wordMapper, times(words.size())).toWordResponseDTO(any(Word.class));
    }

    @Test
    void getWordsByLanguageTo_NoWordsFound() {
        // Arrange
        String language = "English";
        when(dictionaryRepository.findByToLang(language)).thenReturn(List.of(new Dictionary()));
        when(wordRepository.findByDictionaryIdIn(any())).thenReturn(new ArrayList<>());

        // Act
        List<WordResponseDTO> result = wordService.getWordsByLanguageTo(language);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(dictionaryRepository).findByToLang(language);
        verify(wordRepository).findByDictionaryIdIn(any());
        verifyNoInteractions(wordMapper);
    }


    @Test
    void getWordsByUserId_Success() {
        // Arrange
        String userId = "user123";
        List<String> dictIds = List.of("1", "2");
        List<Word> words = List.of(new Word());
        Dictionary d1 = Dictionary.builder().dictionaryId("1")
                .userId("1")
                .build();

        Dictionary d2 = Dictionary.builder().dictionaryId("2")
                .fromLang("2")
                .build();

        when(dictionaryRepository.findByUserId(userId)).thenReturn(List.of(d1, d2));
        when(wordRepository.findByDictionaryIdIn(dictIds)).thenReturn(words);
        when(wordMapper.toWordResponseDTO(any())).thenReturn(new WordResponseDTO());

        // Act
        List<WordResponseDTO> result = wordService.getWordsByUserId(userId);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
        verify(dictionaryRepository).findByUserId(userId);
        verify(wordRepository).findByDictionaryIdIn(dictIds);
        verify(wordMapper, times(words.size())).toWordResponseDTO(any());
    }

    @Test
    void getWordsByUserId_NoWordsFound() {
        // Arrange
        String userId = "user123";
        when(dictionaryRepository.findByUserId(userId)).thenReturn(List.of());

        // Act
        List<WordResponseDTO> result = wordService.getWordsByUserId(userId);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(dictionaryRepository).findByUserId(userId);
    }


    @Test
    void getWordsByDictionaryIds_Success() {
        // Arrange
        List<String> dictionaryIds = List.of("1", "2");
        List<Word> words = List.of(new Word());
        when(wordRepository.findByDictionaryIdIn(dictionaryIds)).thenReturn(words);
        when(wordMapper.toWordResponseDTO(any())).thenReturn(new WordResponseDTO());

        // Act
        List<WordResponseDTO> result = wordService.getWordsByDictionaryIds(dictionaryIds);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
        verify(wordRepository).findByDictionaryIdIn(dictionaryIds);
        verify(wordMapper, times(words.size())).toWordResponseDTO(any());
    }

    @Test
    void getWordsByDictionaryIds_NoWordsFound() {
        // Arrange
        List<String> dictionaryIds = List.of("1", "2");
        when(wordRepository.findByDictionaryIdIn(dictionaryIds)).thenReturn(List.of());

        // Act
        List<WordResponseDTO> result = wordService.getWordsByDictionaryIds(dictionaryIds);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(wordRepository).findByDictionaryIdIn(dictionaryIds);
        verifyNoInteractions(wordMapper);
    }
}

