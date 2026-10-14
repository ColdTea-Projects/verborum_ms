package de.coldtea.verborum.msdictionary.dictionary.service.impl;

import de.coldtea.verborum.msdictionary.common.mapper.DictionaryMapper;
import de.coldtea.verborum.msdictionary.common.utils.DictionaryChangeNotifier;
import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryRequestDTO;
import de.coldtea.verborum.msdictionary.dictionary.entity.Dictionary;
import de.coldtea.verborum.msdictionary.dictionary.repository.DictionaryRepository;
import de.coldtea.verborum.msdictionary.marketplacemember.repository.MarketplaceMemberRepository;
import de.coldtea.verborum.msdictionary.tag.repository.DictionaryTagRepository;
import de.coldtea.verborum.msdictionary.word.repository.WordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * `dictionary.changed` (P4N-06) from DictionaryServiceImpl: the sync nudge fires on a real change and
 * never on a re-upload of identical content (that is what stops two devices ping-ponging).
 */
class DictionaryServiceImplSyncEventTest {

    private static final String OWNER = "user1";
    private static final String ID = "dict-1";

    @Mock
    private DictionaryRepository dictionaryRepository;
    @Mock
    private WordRepository wordRepository;
    @Mock
    private DictionaryMapper dictionaryMapper;
    @Mock
    private DictionaryTagRepository dictionaryTagRepository;
    @Mock
    private MarketplaceMemberRepository marketplaceMemberRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private DictionaryChangeNotifier changeNotifier;

    @InjectMocks
    private DictionaryServiceImpl dictionaryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private static Dictionary dictionary(String name, String toLang, boolean isPublic) {
        return Dictionary.builder().dictionaryId(ID).userId(OWNER).name(name).fromLang("EN").toLang(toLang)
                .isPublic(isPublic).build();
    }

    private static DictionaryRequestDTO request(boolean isPublic) {
        DictionaryRequestDTO dto = new DictionaryRequestDTO();
        dto.setDictionaryId(ID);
        dto.setIsPublic(isPublic);
        return dto;
    }

    /** The mapper returns what the client sent; `existing` is what the database held before the save. */
    private void givenSave(Dictionary existing, Dictionary incoming, DictionaryRequestDTO dto) {
        when(dictionaryRepository.findById(ID)).thenReturn(Optional.ofNullable(existing));
        when(dictionaryRepository.countByUserId(OWNER)).thenReturn(0L);
        when(dictionaryMapper.toDictionary(dto)).thenReturn(incoming);
        when(dictionaryRepository.saveAndFlush(incoming)).thenReturn(incoming);
    }

    @Test
    void saveDictionary_Create_NotifiesTheOtherDevices() {
        // Arrange
        DictionaryRequestDTO dto = request(false);
        givenSave(null, dictionary("Travel", "DE", false), dto);

        // Act
        dictionaryService.saveDictionary(dto, OWNER);

        // Assert
        verify(changeNotifier).dictionaryChanged(OWNER, ID);
    }

    @Test
    void saveDictionary_Rename_NotifiesTheOtherDevices() {
        // Arrange
        DictionaryRequestDTO dto = request(false);
        givenSave(dictionary("Travel", "DE", false), dictionary("Trips", "DE", false), dto);

        // Act
        dictionaryService.saveDictionary(dto, OWNER);

        // Assert
        verify(changeNotifier).dictionaryChanged(OWNER, ID);
    }

    @Test
    void saveDictionary_VisibilityFlip_NotifiesTheOtherDevices() {
        // Arrange — public becomes private: a different value on the user's other devices
        DictionaryRequestDTO dto = request(false);
        givenSave(dictionary("Travel", "DE", true), dictionary("Travel", "DE", false), dto);

        // Act
        dictionaryService.saveDictionary(dto, OWNER);

        // Assert
        verify(changeNotifier).dictionaryChanged(OWNER, ID);
    }

    @Test
    void saveDictionary_IdenticalReUpload_NotifiesNobody() {
        // Arrange — a client that re-uploads everything on each sync must not trigger a push storm
        DictionaryRequestDTO dto = request(false);
        givenSave(dictionary("Travel", "DE", false), dictionary("Travel", "DE", false), dto);

        // Act
        dictionaryService.saveDictionary(dto, OWNER);

        // Assert
        verifyNoInteractions(changeNotifier);
    }

    @Test
    void deleteDictionary_Existing_NotifiesTheOtherDevices() {
        // Arrange
        when(dictionaryRepository.findById(ID)).thenReturn(Optional.of(dictionary("Travel", "DE", false)));

        // Act
        dictionaryService.deleteDictionary(ID, OWNER);

        // Assert
        verify(changeNotifier).dictionaryChanged(OWNER, ID);
    }

    @Test
    void deleteDictionary_UnknownId_NotifiesNobody() {
        // Arrange
        when(dictionaryRepository.findById(ID)).thenReturn(Optional.empty());

        // Act
        dictionaryService.deleteDictionary(ID, OWNER);

        // Assert
        verifyNoInteractions(changeNotifier);
    }

    @Test
    void setVisibilityOfAll_NotifiesOncePerDictionaryThatChanged() {
        // Arrange — joining the Forum: two private dictionaries flip, one is already public
        Dictionary a = Dictionary.builder().dictionaryId("a").userId(OWNER).name("A").fromLang("EN").toLang("DE").isPublic(false).build();
        Dictionary b = Dictionary.builder().dictionaryId("b").userId(OWNER).name("B").fromLang("EN").toLang("DE").isPublic(false).build();
        Dictionary c = Dictionary.builder().dictionaryId("c").userId(OWNER).name("C").fromLang("EN").toLang("DE").isPublic(true).build();
        when(dictionaryRepository.findByUserId(OWNER)).thenReturn(List.of(a, b, c));
        when(dictionaryRepository.saveAndFlush(any(Dictionary.class))).thenAnswer(call -> call.getArgument(0));

        // Act
        int changed = dictionaryService.setVisibilityOfAll(OWNER, true);

        // Assert
        assertEquals(2, changed);
        verify(changeNotifier).dictionaryChanged(OWNER, "a");
        verify(changeNotifier).dictionaryChanged(OWNER, "b");
        verify(changeNotifier, never()).dictionaryChanged(OWNER, "c");
    }

    @Test
    void publishTagChange_PrivateDictionary_StillNotifiesButPublishesNoListingUpdate() {
        // Arrange — tags are part of what the user's other devices hold, public or not
        when(dictionaryRepository.findById(ID)).thenReturn(Optional.of(dictionary("Travel", "DE", false)));

        // Act
        dictionaryService.publishTagChange(ID);

        // Assert
        verify(changeNotifier).dictionaryChanged(OWNER, ID);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void publishTagChange_PublicDictionary_NotifiesAndKeepsTheListingUpdate() {
        // Arrange
        Dictionary stored = dictionary("Travel", "DE", true);
        when(dictionaryRepository.findById(ID)).thenReturn(Optional.of(stored));
        when(dictionaryRepository.saveAndFlush(stored)).thenReturn(stored);
        when(dictionaryTagRepository.findByDictionaryId(ID)).thenReturn(List.of());

        // Act
        dictionaryService.publishTagChange(ID);

        // Assert — the existing marketplace behaviour is untouched
        verify(changeNotifier).dictionaryChanged(OWNER, ID);
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void publishTagChange_UnknownDictionary_NotifiesNobody() {
        // Arrange
        when(dictionaryRepository.findById(anyString())).thenReturn(Optional.empty());

        // Act
        dictionaryService.publishTagChange(ID);

        // Assert
        verifyNoInteractions(changeNotifier);
    }
}
