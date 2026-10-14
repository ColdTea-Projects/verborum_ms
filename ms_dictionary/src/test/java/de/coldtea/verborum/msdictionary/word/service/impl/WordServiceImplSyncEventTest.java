package de.coldtea.verborum.msdictionary.word.service.impl;

import de.coldtea.verborum.msdictionary.common.mapper.WordMapper;
import de.coldtea.verborum.msdictionary.common.utils.DictionaryChangeNotifier;
import de.coldtea.verborum.msdictionary.dictionary.entity.Dictionary;
import de.coldtea.verborum.msdictionary.dictionary.repository.DictionaryRepository;
import de.coldtea.verborum.msdictionary.word.dto.WordBundleRequestDTO;
import de.coldtea.verborum.msdictionary.word.dto.WordRequestDTO;
import de.coldtea.verborum.msdictionary.word.entity.Word;
import de.coldtea.verborum.msdictionary.word.repository.WordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * `dictionary.changed` (P4N-06) from WordServiceImpl: one nudge per dictionary touched, and none when
 * the uploaded content equals what is stored (a client that re-uploads everything on each sync).
 */
class WordServiceImplSyncEventTest {

    private static final String OWNER = "user1";
    private static final String DICT = "dict-1";

    @Mock
    private WordRepository wordRepository;
    @Mock
    private DictionaryRepository dictionaryRepository;
    @Mock
    private WordMapper wordMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private DictionaryChangeNotifier changeNotifier;

    @InjectMocks
    private WordServiceImpl wordService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Dictionary dictionary = Dictionary.builder().dictionaryId(DICT).userId(OWNER).fromLang("EN").toLang("DE").build();
        when(dictionaryRepository.findById(DICT)).thenReturn(Optional.of(dictionary));
        when(dictionaryRepository.findAllById(any())).thenReturn(List.of(dictionary));
    }

    private static Word word(String id, String text, Integer level) {
        return Word.builder().wordId(id).dictionaryId(DICT).word(text).wordMeta("{\"lang\":\"en\"}")
                .translation("Haus").translationMeta("{\"lang\":\"de\"}").level(level).build();
    }

    private static List<WordBundleRequestDTO> bundleOf(int words) {
        return List.of(new WordBundleRequestDTO(DICT,
                java.util.stream.IntStream.range(0, words).mapToObj(i -> new WordRequestDTO()).toList()));
    }

    @Test
    void saveWords_NewWord_NotifiesOnceForTheDictionary() {
        // Arrange
        when(wordMapper.toWord(eq(DICT), any(WordRequestDTO.class))).thenReturn(word("w1", "house", 0));
        when(wordRepository.findAllById(List.of("w1"))).thenReturn(List.of());

        // Act
        wordService.saveWords(bundleOf(1), OWNER);

        // Assert
        verify(changeNotifier).dictionaryChanged(OWNER, DICT);
    }

    @Test
    void saveWords_BulkUpload_IsOneChangeNotThreeHundred() {
        // Arrange — three new words in the same dictionary
        when(wordMapper.toWord(eq(DICT), any(WordRequestDTO.class)))
                .thenReturn(word("w1", "a", 0), word("w2", "b", 0), word("w3", "c", 0));
        when(wordRepository.findAllById(List.of("w1", "w2", "w3"))).thenReturn(List.of());

        // Act
        wordService.saveWords(bundleOf(3), OWNER);

        // Assert
        verify(changeNotifier, times(1)).dictionaryChanged(OWNER, DICT);
    }

    @Test
    void saveWords_IdenticalReUpload_NotifiesNobody() {
        // Arrange — stored and incoming are equal in every field a client edits
        when(wordMapper.toWord(eq(DICT), any(WordRequestDTO.class))).thenReturn(word("w1", "house", 2));
        when(wordRepository.findAllById(List.of("w1"))).thenReturn(List.of(word("w1", "house", 2)));

        // Act
        wordService.saveWords(bundleOf(1), OWNER);

        // Assert
        verifyNoInteractions(changeNotifier);
    }

    @Test
    void saveWords_EditedLevel_NotifiesTheOtherDevices() {
        // Arrange — mastery level is client-owned progress that must reach the user's other devices
        when(wordMapper.toWord(eq(DICT), any(WordRequestDTO.class))).thenReturn(word("w1", "house", 3));
        when(wordRepository.findAllById(List.of("w1"))).thenReturn(List.of(word("w1", "house", 2)));

        // Act
        wordService.saveWords(bundleOf(1), OWNER);

        // Assert
        verify(changeNotifier).dictionaryChanged(OWNER, DICT);
    }

    @Test
    void saveWords_EditedText_NotifiesTheOtherDevices() {
        // Arrange
        when(wordMapper.toWord(eq(DICT), any(WordRequestDTO.class))).thenReturn(word("w1", "home", 2));
        when(wordRepository.findAllById(List.of("w1"))).thenReturn(List.of(word("w1", "house", 2)));

        // Act
        wordService.saveWords(bundleOf(1), OWNER);

        // Assert
        verify(changeNotifier).dictionaryChanged(OWNER, DICT);
    }

    @Test
    void deleteWords_OwnedWord_NotifiesTheOtherDevices() {
        // Arrange
        when(wordRepository.findAllById(List.of("w1"))).thenReturn(List.of(word("w1", "house", 0)));

        // Act
        wordService.deleteWords(List.of("w1"), OWNER);

        // Assert
        verify(changeNotifier).dictionaryChanged(OWNER, DICT);
    }

    @Test
    void deleteWords_NothingOwned_NotifiesNobody() {
        // Arrange — someone else's word is dropped silently, so nothing changed for this user
        Dictionary someoneElses = Dictionary.builder().dictionaryId("other").userId("stranger").build();
        when(wordRepository.findAllById(List.of("w9"))).thenReturn(List.of(
                Word.builder().wordId("w9").dictionaryId("other").build()));
        when(dictionaryRepository.findById("other")).thenReturn(Optional.of(someoneElses));

        // Act
        wordService.deleteWords(List.of("w9"), OWNER);

        // Assert
        verifyNoInteractions(changeNotifier);
    }

    @Test
    void deleteWordsByDictionaryId_WithWords_NotifiesTheOtherDevices() {
        // Arrange
        when(wordRepository.countByDictionaryId(DICT)).thenReturn(4L);

        // Act
        wordService.deleteWordsByDictionaryId(DICT, OWNER);

        // Assert
        verify(changeNotifier).dictionaryChanged(OWNER, DICT);
    }

    @Test
    void deleteWordsByDictionaryId_AlreadyEmpty_NotifiesNobody() {
        // Arrange
        when(wordRepository.countByDictionaryId(DICT)).thenReturn(0L);

        // Act
        wordService.deleteWordsByDictionaryId(DICT, OWNER);

        // Assert
        verifyNoInteractions(changeNotifier);
    }
}
