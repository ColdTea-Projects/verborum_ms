package de.coldtea.verborum.msdictionary.word.service.impl;

import de.coldtea.verborum.msdictionary.common.event.OutboundEvent;
import de.coldtea.verborum.msdictionary.common.event.WordCreatedEvent;
import de.coldtea.verborum.msdictionary.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msdictionary.common.exception.QuotaExceededException;
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
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_WORD_CREATED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WordServiceImplTest {

    /** The JWT subject of the caller. Matches the fixture dictionaries' userId (P3-05). */
    private static final String OWNER = "user1";

    @Mock
    private WordRepository wordRepository;

    @Mock
    private DictionaryRepository dictionaryRepository;

    @Mock
    private WordMapper wordMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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
        assertDoesNotThrow(() -> wordService.saveWords(wordBundles, OWNER));

        // Assert
        verify(dictionaryRepository).findById(dictionaryId);
        verify(wordRepository).saveAllAndFlush(any());
        verifyNoInteractions(eventPublisher);
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
        wordService.saveWords(wordBundles, OWNER);

        // Assert
        OutboundEvent outbound = capturedEvents().get(0);
        assertEquals(ROUTING_KEY_WORD_CREATED, outbound.routingKey());

        WordCreatedEvent event = (WordCreatedEvent) outbound.payload();
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
        wordService.saveWords(wordBundles, OWNER);

        // Assert
        verify(wordRepository).saveAllAndFlush(any());
        verifyNoInteractions(eventPublisher);
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
        wordService.saveWords(wordBundles, OWNER);

        // Assert
        List<OutboundEvent> raised = capturedEvents();
        assertEquals(1, raised.size());
        assertEquals("brandNew", ((WordCreatedEvent) raised.get(0).payload()).getWordId());
    }

    @Test
    void saveWords_ExistingWordOfAnotherUsersDictionary_ThrowsForbiddenAndSavesNothing() {
        // SEC-01: the caller owns the target dictionary, but the wordId belongs to a word in someone
        // else's — the upsert would move it out of the victim's dictionary into the caller's
        // Arrange
        String ownDictionaryId = "own";
        List<WordBundleRequestDTO> wordBundles = List.of(new WordBundleRequestDTO(ownDictionaryId, List.of(new WordRequestDTO())));

        when(dictionaryRepository.findById(ownDictionaryId)).thenReturn(Optional.of(dictionary(ownDictionaryId)));
        when(wordMapper.toWord(eq(ownDictionaryId), any(WordRequestDTO.class))).thenReturn(word("victimWord", ownDictionaryId));
        when(wordRepository.findAllById(List.of("victimWord"))).thenReturn(List.of(word("victimWord", "victimsDictionary")));

        // Act & Assert
        assertThrows(ForbiddenOperationException.class, () -> wordService.saveWords(wordBundles, OWNER));
        verify(wordRepository, never()).saveAllAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void saveWords_ExistingWordMovedBetweenOwnDictionaries_ThrowsForbidden() {
        // A word keeps its dictionary even between two of the caller's own — nothing announces a move,
        // and the check cannot tell the two cases apart without a second ownership lookup
        // Arrange
        List<WordBundleRequestDTO> wordBundles = List.of(new WordBundleRequestDTO("ownB", List.of(new WordRequestDTO())));

        when(dictionaryRepository.findById("ownB")).thenReturn(Optional.of(dictionary("ownB")));
        when(wordMapper.toWord(eq("ownB"), any(WordRequestDTO.class))).thenReturn(word("myWord", "ownB"));
        when(wordRepository.findAllById(List.of("myWord"))).thenReturn(List.of(word("myWord", "ownA")));

        // Act & Assert
        assertThrows(ForbiddenOperationException.class, () -> wordService.saveWords(wordBundles, OWNER));
        verify(wordRepository, never()).saveAllAndFlush(any());
    }

    /**
     * The service raises OutboundEvents; OutboundEventPublisher sends them after commit (rule 1),
     * so these tests assert on what was raised rather than on RabbitTemplate.
     */
    private List<OutboundEvent> capturedEvents() {
        ArgumentCaptor<OutboundEvent> captor = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        return captor.getAllValues();
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
        assertThrows(RecordNotFoundException.class, () -> wordService.saveWords(wordBundles, OWNER));
        verifyNoInteractions(eventPublisher);
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
        assertThrows(RecordNotFoundException.class, () -> wordService.saveWords(wordBundles, OWNER));
    }

    @Test
    void deleteWords_Success() {
        // Arrange
        List<String> wordIdList = new ArrayList<>();

        // Act
        assertDoesNotThrow(() -> wordService.deleteWords(wordIdList, OWNER));

        // Assert — an empty request deletes nothing at all: the ownership filter resolves an empty
        // set of owned ids, and an empty IN (...) delete would be pointless (P3-08)
        verify(wordRepository).findAllById(wordIdList);
        verify(wordRepository, never()).deleteAllById(any());
    }


    @Test
    void deleteWordsByDictionaryId_Success() {
        // Arrange
        String dictionaryId = "1";
        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.of(dictionary(dictionaryId)));

        // Act
        assertDoesNotThrow(() -> wordService.deleteWordsByDictionaryId(dictionaryId, OWNER));

        // Assert
        verify(wordRepository).deleteWordsByDictionaryId(dictionaryId);
    }

    @Test
    void deleteWordsByDictionaryId_DictionaryNotFound() {
        // Arrange
        String dictionaryId = "1";
        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RecordNotFoundException.class, () -> wordService.deleteWordsByDictionaryId(dictionaryId, OWNER));
    }


    @Test
    void getWordsByLanguageFrom_Success() {
        // Arrange
        String language = "EN";
        List<String> dictIds = List.of("1", "2");
        List<Word> words = new ArrayList<>();
        words.add(new Word());

        Dictionary d1 = Dictionary.builder().dictionaryId("1")
                .userId(OWNER)
                .fromLang(language)
                .build();

        Dictionary d2 = Dictionary.builder().dictionaryId("2")
                .userId(OWNER)
                .fromLang(language)
                .build();
        when(dictionaryRepository.findByFromLang(language)).thenReturn(List.of(d1, d2));
        when(wordRepository.findByDictionaryIdIn(dictIds)).thenReturn(words);
        when(wordMapper.toWordResponseDTO(any(Word.class))).thenReturn(new WordResponseDTO());

        // Act
        List<WordResponseDTO> result = wordService.getWordsByLanguageFrom(language, OWNER);

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
                .userId(OWNER)
                .fromLang(language)
                .build();

        Dictionary d2 = Dictionary.builder().dictionaryId("2")
                .userId(OWNER)
                .fromLang(language)
                .build();
        when(dictionaryRepository.findByToLang(language)).thenReturn(List.of(d1, d2));
        when(wordRepository.findByDictionaryIdIn(dictIds)).thenReturn(words);
        when(wordMapper.toWordResponseDTO(any(Word.class))).thenReturn(new WordResponseDTO());

        // Act
        List<WordResponseDTO> result = wordService.getWordsByLanguageTo(language, OWNER);

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
        List<WordResponseDTO> result = wordService.getWordsByLanguageTo(language, OWNER);

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
    void getWordsByDictionary_Success() {
        // Arrange
        List<String> dictionaryIds = List.of("1");
        List<Word> words = List.of(new Word());
        when(dictionaryRepository.findById("1")).thenReturn(Optional.of(dictionary("1")));
        when(wordRepository.findByDictionaryIdIn(dictionaryIds)).thenReturn(words);
        when(wordMapper.toWordResponseDTO(any())).thenReturn(new WordResponseDTO());

        // Act
        List<WordResponseDTO> result = wordService.getWordsByDictionary("1", OWNER);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
        verify(wordRepository).findByDictionaryIdIn(dictionaryIds);
        verify(wordMapper, times(words.size())).toWordResponseDTO(any());
    }

    @Test
    void getWordsByDictionary_NoWordsFound() {
        // Arrange
        List<String> dictionaryIds = List.of("1");
        when(dictionaryRepository.findById("1")).thenReturn(Optional.of(dictionary("1")));
        when(wordRepository.findByDictionaryIdIn(dictionaryIds)).thenReturn(List.of());

        // Act
        List<WordResponseDTO> result = wordService.getWordsByDictionary("1", OWNER);

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
        List<Word> words = List.of(word("1", "dict1"), word("2", "dict1"));
        // a word carries no owner of its own, so readability resolves through its dictionary (P3-08,
        // P4-10) — one batch lookup for all the words' dictionaries
        when(dictionaryRepository.findAllById(List.of("dict1"))).thenReturn(List.of(dictionary("dict1")));
        when(wordRepository.findAllById(wordIds)).thenReturn(words);
        when(wordMapper.toWordResponseDTO(any())).thenReturn(new WordResponseDTO());

        // Act
        List<WordResponseDTO> result = wordService.getWordsByIds(wordIds, OWNER);

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
        List<WordResponseDTO> result = wordService.getWordsByIds(wordIds, OWNER);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(wordRepository).findAllById(wordIds);
        verifyNoInteractions(wordMapper);
    }

    @Test
    void saveWords_IntoAnotherUsersDictionary_IsForbidden() {
        // Arrange — a word inherits its owner from its dictionary, so this is the same hole as
        // claiming another userId outright
        String dictionaryId = "1";
        List<WordBundleRequestDTO> bundles = List.of(
                new WordBundleRequestDTO(dictionaryId, List.of(new WordRequestDTO())));
        Dictionary someoneElses = Dictionary.builder().dictionaryId(dictionaryId).userId("someone-else").build();

        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.of(someoneElses));

        // Act & Assert
        assertThrows(ForbiddenOperationException.class, () -> wordService.saveWords(bundles, OWNER));
        verify(wordRepository, never()).saveAllAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deleteWordsByDictionaryId_AnotherUsersDictionary_IsForbidden() {
        // Arrange
        String dictionaryId = "1";
        when(dictionaryRepository.findById(dictionaryId))
                .thenReturn(Optional.of(Dictionary.builder().dictionaryId(dictionaryId).userId("someone-else").build()));

        // Act & Assert
        assertThrows(ForbiddenOperationException.class,
                () -> wordService.deleteWordsByDictionaryId(dictionaryId, OWNER));
        verify(wordRepository, never()).deleteWordsByDictionaryId(anyString());
    }

    @Test
    void deleteWords_SkipsWordsInAnotherUsersDictionary() {
        // Arrange — dropped rather than refused, so the response cannot be used to probe word ids
        when(wordRepository.findAllById(List.of("mine", "theirs")))
                .thenReturn(List.of(word("mine", "myDict"), word("theirs", "theirDict")));
        when(dictionaryRepository.findById("myDict")).thenReturn(Optional.of(dictionary("myDict")));
        when(dictionaryRepository.findById("theirDict"))
                .thenReturn(Optional.of(Dictionary.builder().dictionaryId("theirDict").userId("someone-else").build()));

        // Act
        wordService.deleteWords(List.of("mine", "theirs"), OWNER);

        // Assert
        verify(wordRepository).deleteAllById(List.of("mine"));
    }

    @Test
    void getWordsByLanguageFrom_ExcludesOtherUsersWords() {
        // Arrange — this endpoint used to return every user's words for the language
        String language = "EN";
        when(dictionaryRepository.findByFromLang(language)).thenReturn(List.of(
                dictionary("mine"),
                Dictionary.builder().dictionaryId("theirs").userId("someone-else").fromLang(language).build()));
        when(wordRepository.findByDictionaryIdIn(List.of("mine"))).thenReturn(List.of(word("w1", "mine")));
        when(wordMapper.toWordResponseDTO(any())).thenReturn(new WordResponseDTO());

        // Act
        List<WordResponseDTO> result = wordService.getWordsByLanguageFrom(language, OWNER);

        // Assert
        assertEquals(1, result.size());
        verify(wordRepository).findByDictionaryIdIn(List.of("mine"));
    }

    // ---- P4-10: public dictionaries are readable by any authenticated user ----

    @Test
    void getWordsByDictionary_AnotherUsersPublicDictionary_ReturnsWordsWithoutLevel() {
        // Arrange — how an imported marketplace dictionary is opened
        Dictionary theirs = dictionary("theirs", "someone-else", true);
        Word word = word("w1", "theirs");
        WordResponseDTO mapped = new WordResponseDTO();
        mapped.setLevel(4);
        when(dictionaryRepository.findById("theirs")).thenReturn(Optional.of(theirs));
        when(wordRepository.findByDictionaryIdIn(List.of("theirs"))).thenReturn(List.of(word));
        when(wordMapper.toWordResponseDTO(word)).thenReturn(mapped);

        // Act
        List<WordResponseDTO> result = wordService.getWordsByDictionary("theirs", OWNER);

        // Assert — level is the owner's mastery, not the reader's
        assertEquals(1, result.size());
        assertNull(result.get(0).getLevel());
    }

    @Test
    void getWordsByDictionary_OwnPublicDictionary_KeepsLevel() {
        // Arrange
        Dictionary mine = dictionary("mine", OWNER, true);
        Word word = word("w1", "mine");
        WordResponseDTO mapped = new WordResponseDTO();
        mapped.setLevel(4);
        when(dictionaryRepository.findById("mine")).thenReturn(Optional.of(mine));
        when(wordRepository.findByDictionaryIdIn(List.of("mine"))).thenReturn(List.of(word));
        when(wordMapper.toWordResponseDTO(word)).thenReturn(mapped);

        // Act
        List<WordResponseDTO> result = wordService.getWordsByDictionary("mine", OWNER);

        // Assert
        assertEquals(4, result.get(0).getLevel());
    }

    @Test
    void getWordsByDictionary_AnotherUsersPrivateDictionary_Is404() {
        // Arrange — by id, so the read rule: indistinguishable from not existing (SEC-14)
        when(dictionaryRepository.findById("theirs")).thenReturn(Optional.of(dictionary("theirs", "someone-else", false)));

        // Act & Assert
        assertThrows(RecordNotFoundException.class, () -> wordService.getWordsByDictionary("theirs", OWNER));
        verify(wordRepository, never()).findByDictionaryIdIn(any());
    }

    @Test
    void getWordsByDictionary_UnknownDictionary_Is404() {
        // Arrange
        when(dictionaryRepository.findById("gone")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RecordNotFoundException.class, () -> wordService.getWordsByDictionary("gone", OWNER));
        verifyNoInteractions(wordRepository);
    }

    @Test
    void getWordsByIds_MixedDictionaries_ReturnsOwnAndPublicOnly() {
        // Arrange
        List<String> wordIds = List.of("w-mine", "w-public", "w-private");
        Word inMine = word("w-mine", "mine");
        Word inPublic = word("w-public", "public");
        Word inPrivate = word("w-private", "private");
        when(wordRepository.findAllById(wordIds)).thenReturn(List.of(inMine, inPublic, inPrivate));
        when(dictionaryRepository.findAllById(List.of("mine", "public", "private"))).thenReturn(List.of(
                dictionary("mine", OWNER, false),
                dictionary("public", "someone-else", true),
                dictionary("private", "someone-else", false)));
        WordResponseDTO mineDto = new WordResponseDTO();
        mineDto.setLevel(2);
        WordResponseDTO publicDto = new WordResponseDTO();
        publicDto.setLevel(5);
        when(wordMapper.toWordResponseDTO(inMine)).thenReturn(mineDto);
        when(wordMapper.toWordResponseDTO(inPublic)).thenReturn(publicDto);

        // Act
        List<WordResponseDTO> result = wordService.getWordsByIds(wordIds, OWNER);

        // Assert
        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getLevel());
        assertNull(result.get(1).getLevel());
        verify(wordMapper, never()).toWordResponseDTO(inPrivate);
    }

    private static Dictionary dictionary(String dictionaryId, String userId, Boolean isPublic) {
        return Dictionary.builder()
                .dictionaryId(dictionaryId)
                .userId(userId)
                .isPublic(isPublic)
                .fromLang("EN")
                .toLang("DE")
                .build();
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

    // ---- SEC-07: per-dictionary word quota ----

    @Test
    void saveWords_NewWordOverTheQuota_ThrowsAndSavesNothing() {
        // Arrange
        String dictionaryId = "1";
        List<WordBundleRequestDTO> wordBundles = List.of(new WordBundleRequestDTO(dictionaryId, List.of(new WordRequestDTO())));
        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.of(dictionary(dictionaryId)));
        when(wordMapper.toWord(eq(dictionaryId), any(WordRequestDTO.class))).thenReturn(word("new", dictionaryId));
        when(wordRepository.findAllById(List.of("new"))).thenReturn(List.of());
        when(wordRepository.countByDictionaryId(dictionaryId)).thenReturn(5000L);

        // Act & Assert
        assertThrows(QuotaExceededException.class, () -> wordService.saveWords(wordBundles, OWNER));
        verify(wordRepository, never()).saveAllAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void saveWords_EditAtTheQuota_IsNotCounted() {
        // Arrange — a full dictionary can still have its words edited
        String dictionaryId = "1";
        List<WordBundleRequestDTO> wordBundles = List.of(new WordBundleRequestDTO(dictionaryId, List.of(new WordRequestDTO())));
        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.of(dictionary(dictionaryId)));
        when(wordMapper.toWord(eq(dictionaryId), any(WordRequestDTO.class))).thenReturn(word("stored", dictionaryId));
        when(wordRepository.findAllById(List.of("stored"))).thenReturn(List.of(word("stored", dictionaryId)));
        when(wordRepository.countByDictionaryId(dictionaryId)).thenReturn(5000L);

        // Act
        wordService.saveWords(wordBundles, OWNER);

        // Assert
        verify(wordRepository).saveAllAndFlush(any());
        verify(wordRepository, never()).countByDictionaryId(any());
    }
}
