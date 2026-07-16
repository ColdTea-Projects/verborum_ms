package de.coldtea.verborum.msdictionary.word.service.impl;

import de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig;
import de.coldtea.verborum.msdictionary.common.event.WordCreatedEvent;
import de.coldtea.verborum.msdictionary.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msdictionary.common.mapper.WordMapper;
import de.coldtea.verborum.msdictionary.dictionary.entity.Dictionary;
import de.coldtea.verborum.msdictionary.dictionary.repository.DictionaryRepository;
import de.coldtea.verborum.msdictionary.word.dto.WordBundleRequestDTO;
import de.coldtea.verborum.msdictionary.word.dto.WordResponseDTO;
import de.coldtea.verborum.msdictionary.word.entity.Word;
import de.coldtea.verborum.msdictionary.word.repository.WordRepository;
import de.coldtea.verborum.msdictionary.word.dto.WordRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_WORD_CREATED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WordServiceImplTest {

    @Mock
    private WordRepository wordRepository;

    @Mock
    private DictionaryRepository dictionaryRepository;

    @Mock
    private WordMapper wordMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

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
        List<WordBundleRequestDTO> wordBundles = new ArrayList<>();
        wordBundles.add(new WordBundleRequestDTO(dictionaryId, wordList));

        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.of(dictionary(dictionaryId)));
        when(wordMapper.toWord(dictionaryId, new WordRequestDTO())).thenReturn(word("word1", dictionaryId));
        // already stored, so this test stays about the save itself — publishing is covered below
        when(wordRepository.findAllById(List.of("word1"))).thenReturn(List.of(word("word1", dictionaryId)));

        // Act
        assertDoesNotThrow(() -> wordService.saveWords(wordBundles));

        // Assert
        verify(dictionaryRepository).findById(dictionaryId);
        verify(wordRepository).saveAllAndFlush(any());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void saveWords_NewWord_PublishesWordCreatedEvent() {
        // Arrange
        String dictionaryId = "1";
        List<WordBundleRequestDTO> wordBundles = List.of(new WordBundleRequestDTO(dictionaryId, List.of(new WordRequestDTO())));

        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.of(dictionary(dictionaryId)));
        when(dictionaryRepository.findAllById(List.of(dictionaryId))).thenReturn(List.of(dictionary(dictionaryId)));
        when(wordMapper.toWord(eq(dictionaryId), any(WordRequestDTO.class))).thenReturn(word("word1", dictionaryId));
        when(wordRepository.findAllById(List.of("word1"))).thenReturn(List.of());

        // Act
        wordService.saveWords(wordBundles);

        // Assert
        ArgumentCaptor<WordCreatedEvent> captor = ArgumentCaptor.forClass(WordCreatedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq(ROUTING_KEY_WORD_CREATED), captor.capture());

        WordCreatedEvent event = captor.getValue();
        assertEquals("word1", event.getWordId());
        assertEquals(dictionaryId, event.getDictionaryId());
        assertEquals("house", event.getWord());
        assertEquals("Haus", event.getTranslation());
        // userId/fromLang/toLang are carried over from the Dictionary, not the Word
        assertEquals("user1", event.getUserId());
        assertEquals("EN", event.getFromLang());
        assertEquals("DE", event.getToLang());
    }

    @Test
    void saveWords_ExistingWord_PublishesNothing() {
        // saveWords() backs PUT too — editing a word must not re-announce it as created, or
        // ms_autofil counts the same translation twice
        // Arrange
        String dictionaryId = "1";
        List<WordBundleRequestDTO> wordBundles = List.of(new WordBundleRequestDTO(dictionaryId, List.of(new WordRequestDTO())));

        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.of(dictionary(dictionaryId)));
        when(wordMapper.toWord(eq(dictionaryId), any(WordRequestDTO.class))).thenReturn(word("word1", dictionaryId));
        when(wordRepository.findAllById(List.of("word1"))).thenReturn(List.of(word("word1", dictionaryId)));

        // Act
        wordService.saveWords(wordBundles);

        // Assert
        verify(wordRepository).saveAllAndFlush(any());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void saveWords_MixedNewAndExisting_PublishesOnlyForNewWord() {
        // Arrange
        String dictionaryId = "1";
        List<WordBundleRequestDTO> wordBundles = List.of(
                new WordBundleRequestDTO(dictionaryId, List.of(new WordRequestDTO(), new WordRequestDTO())));

        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.of(dictionary(dictionaryId)));
        when(dictionaryRepository.findAllById(List.of(dictionaryId))).thenReturn(List.of(dictionary(dictionaryId)));
        when(wordMapper.toWord(eq(dictionaryId), any(WordRequestDTO.class)))
                .thenReturn(word("existing", dictionaryId), word("brandNew", dictionaryId));
        when(wordRepository.findAllById(List.of("existing", "brandNew")))
                .thenReturn(List.of(word("existing", dictionaryId)));

        // Act
        wordService.saveWords(wordBundles);

        // Assert
        ArgumentCaptor<WordCreatedEvent> captor = ArgumentCaptor.forClass(WordCreatedEvent.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq(ROUTING_KEY_WORD_CREATED), captor.capture());
        assertEquals("brandNew", captor.getValue().getWordId());
    }

    @Test
    void saveWords_DictionaryVanishesBeforePublish_ThrowsRecordNotFound() {
        // The dictionary is validated early in convertToWordStream, but a concurrent delete could
        // land before the event payload is built. That must surface as a clean RecordNotFound,
        // not an NPE
        // Arrange
        String dictionaryId = "1";
        List<WordBundleRequestDTO> wordBundles = List.of(new WordBundleRequestDTO(dictionaryId, List.of(new WordRequestDTO())));

        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.of(dictionary(dictionaryId)));
        when(wordMapper.toWord(eq(dictionaryId), any(WordRequestDTO.class))).thenReturn(word("word1", dictionaryId));
        when(wordRepository.findAllById(List.of("word1"))).thenReturn(List.of());
        when(dictionaryRepository.findAllById(List.of(dictionaryId))).thenReturn(List.of());

        // Act & Assert
        assertThrows(RecordNotFoundException.class, () -> wordService.saveWords(wordBundles));
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void saveWords_DictionaryNotFound() {
        // Arrange
        String dictionaryId = "1";
        List<WordRequestDTO> wordList = new ArrayList<>();
        wordList.add(new WordRequestDTO());
        List<WordBundleRequestDTO> wordBundles = new ArrayList<>();
        wordBundles.add(new WordBundleRequestDTO(dictionaryId, wordList));

        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RecordNotFoundException.class, () -> wordService.saveWords(wordBundles));
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

    @Test
    void getWordsByIds_Success() {
        // Arrange
        List<String> wordIds = List.of("1", "2");
        List<Word> words = List.of(new Word(), new Word());
        when(wordRepository.findAllById(wordIds)).thenReturn(words);
        when(wordMapper.toWordResponseDTO(any())).thenReturn(new WordResponseDTO());

        // Act
        List<WordResponseDTO> result = wordService.getWordsByIds(wordIds);

        // Assert
        assertEquals(words.size(), result.size());
        verify(wordRepository).findAllById(wordIds);
        verify(wordMapper, times(words.size())).toWordResponseDTO(any());
    }

    @Test
    void getWordsByIds_NoWordsFound() {
        // Arrange
        List<String> wordIds = List.of("1", "2");
        when(wordRepository.findAllById(wordIds)).thenReturn(List.of());

        // Act
        List<WordResponseDTO> result = wordService.getWordsByIds(wordIds);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(wordRepository).findAllById(wordIds);
        verifyNoInteractions(wordMapper);
    }

    private static Dictionary dictionary(String dictionaryId) {
        return Dictionary.builder()
                .dictionaryId(dictionaryId)
                .userId("user1")
                .fromLang("EN")
                .toLang("DE")
                .build();
    }

    private static Word word(String wordId, String dictionaryId) {
        return Word.builder()
                .wordId(wordId)
                .dictionaryId(dictionaryId)
                .word("house")
                .translation("Haus")
                .build();
    }
}

