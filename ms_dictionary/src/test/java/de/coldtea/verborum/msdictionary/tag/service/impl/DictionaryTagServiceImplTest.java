package de.coldtea.verborum.msdictionary.tag.service.impl;

import de.coldtea.verborum.msdictionary.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msdictionary.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msdictionary.common.mapper.DictionaryTagMapper;
import de.coldtea.verborum.msdictionary.dictionary.entity.Dictionary;
import de.coldtea.verborum.msdictionary.dictionary.repository.DictionaryRepository;
import de.coldtea.verborum.msdictionary.tag.dto.DictionaryTagRequestDTO;
import de.coldtea.verborum.msdictionary.tag.dto.DictionaryTagResponseDTO;
import de.coldtea.verborum.msdictionary.tag.entity.DictionaryTag;
import de.coldtea.verborum.msdictionary.tag.repository.DictionaryTagRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DictionaryTagServiceImplTest {

    private static final String OWNER = "user1";
    private static final String DICTIONARY_ID = "dict1";

    @Mock
    private DictionaryTagRepository dictionaryTagRepository;

    @Mock
    private DictionaryRepository dictionaryRepository;

    @Mock
    private DictionaryTagMapper dictionaryTagMapper;

    @InjectMocks
    private DictionaryTagServiceImpl dictionaryTagService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private void givenTheDictionaryBelongsTo(String userId) {
        when(dictionaryRepository.findById(DICTIONARY_ID))
                .thenReturn(Optional.of(Dictionary.builder().dictionaryId(DICTIONARY_ID).userId(userId).build()));
    }

    @Test
    void getTagsByDictionary_Success() {
        // Arrange
        givenTheDictionaryBelongsTo(OWNER);
        DictionaryTag tag = new DictionaryTag();
        DictionaryTagResponseDTO responseDTO = new DictionaryTagResponseDTO();

        when(dictionaryTagRepository.findByDictionaryId(DICTIONARY_ID)).thenReturn(List.of(tag));
        when(dictionaryTagMapper.toDictionaryTagResponseDTO(tag)).thenReturn(responseDTO);

        // Act
        List<DictionaryTagResponseDTO> result = dictionaryTagService.getTagsByDictionary(DICTIONARY_ID, OWNER);

        // Assert
        assertEquals(List.of(responseDTO), result);
    }

    @Test
    void addTag_Success() {
        // Arrange
        givenTheDictionaryBelongsTo(OWNER);
        DictionaryTagRequestDTO requestDTO = new DictionaryTagRequestDTO("travel");
        DictionaryTag saved = new DictionaryTag();
        DictionaryTagResponseDTO responseDTO = new DictionaryTagResponseDTO();

        when(dictionaryTagRepository.findByDictionaryIdAndTag(DICTIONARY_ID, "travel")).thenReturn(Optional.empty());
        when(dictionaryTagRepository.saveAndFlush(any(DictionaryTag.class))).thenReturn(saved);
        when(dictionaryTagMapper.toDictionaryTagResponseDTO(saved)).thenReturn(responseDTO);

        // Act
        DictionaryTagResponseDTO result = dictionaryTagService.addTag(DICTIONARY_ID, requestDTO, OWNER);

        // Assert
        assertEquals(responseDTO, result);
        ArgumentCaptor<DictionaryTag> captor = ArgumentCaptor.forClass(DictionaryTag.class);
        verify(dictionaryTagRepository).saveAndFlush(captor.capture());
        assertEquals(DICTIONARY_ID, captor.getValue().getDictionaryId());
        assertEquals("travel", captor.getValue().getTag());
    }

    @Test
    void addTag_NormalisesTag() {
        // Arrange — tags are grouping keys for the marketplace and the later AI work, so "  FOOD "
        // and "food" must be the same tag rather than two
        givenTheDictionaryBelongsTo(OWNER);
        when(dictionaryTagRepository.findByDictionaryIdAndTag(DICTIONARY_ID, "food")).thenReturn(Optional.empty());
        when(dictionaryTagRepository.saveAndFlush(any(DictionaryTag.class))).thenReturn(new DictionaryTag());

        // Act
        dictionaryTagService.addTag(DICTIONARY_ID, new DictionaryTagRequestDTO("  FOOD "), OWNER);

        // Assert
        ArgumentCaptor<DictionaryTag> captor = ArgumentCaptor.forClass(DictionaryTag.class);
        verify(dictionaryTagRepository).saveAndFlush(captor.capture());
        assertEquals("food", captor.getValue().getTag());
    }

    @Test
    void addTag_AlreadyPresent_ReturnsExistingWithoutSaving() {
        // Arrange — the tag set is a set; re-adding must not trip the composite UNIQUE
        givenTheDictionaryBelongsTo(OWNER);
        DictionaryTag existing = new DictionaryTag();
        DictionaryTagResponseDTO responseDTO = new DictionaryTagResponseDTO();

        when(dictionaryTagRepository.findByDictionaryIdAndTag(DICTIONARY_ID, "travel"))
                .thenReturn(Optional.of(existing));
        when(dictionaryTagMapper.toDictionaryTagResponseDTO(existing)).thenReturn(responseDTO);

        // Act
        DictionaryTagResponseDTO result = dictionaryTagService.addTag(
                DICTIONARY_ID, new DictionaryTagRequestDTO("travel"), OWNER);

        // Assert
        assertEquals(responseDTO, result);
        verify(dictionaryTagRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteTag_Success() {
        // Arrange
        givenTheDictionaryBelongsTo(OWNER);

        // Act
        dictionaryTagService.deleteTag(DICTIONARY_ID, "Travel", OWNER);

        // Assert — normalised on delete too, so removing "Travel" removes what "travel" stored
        verify(dictionaryTagRepository).deleteByDictionaryIdAndTag(DICTIONARY_ID, "travel");
    }

    @Test
    void addTag_AnotherUsersDictionary_IsForbidden() {
        // Arrange
        givenTheDictionaryBelongsTo("someone-else");

        // Act & Assert
        assertThrows(ForbiddenOperationException.class,
                () -> dictionaryTagService.addTag(DICTIONARY_ID, new DictionaryTagRequestDTO("travel"), OWNER));
        verifyNoInteractions(dictionaryTagRepository);
    }

    @Test
    void deleteTag_AnotherUsersDictionary_IsForbidden() {
        // Arrange
        givenTheDictionaryBelongsTo("someone-else");

        // Act & Assert
        assertThrows(ForbiddenOperationException.class,
                () -> dictionaryTagService.deleteTag(DICTIONARY_ID, "travel", OWNER));
        verifyNoInteractions(dictionaryTagRepository);
    }

    @Test
    void getTagsByDictionary_AnotherUsersDictionary_Is404NotForbidden() {
        // Arrange — a 403 would confirm the dictionary exists
        givenTheDictionaryBelongsTo("someone-else");

        // Act & Assert
        assertThrows(RecordNotFoundException.class,
                () -> dictionaryTagService.getTagsByDictionary(DICTIONARY_ID, OWNER));
        verifyNoInteractions(dictionaryTagRepository);
    }

    @Test
    void addTag_UnknownDictionary() {
        // Arrange
        when(dictionaryRepository.findById(DICTIONARY_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RecordNotFoundException.class,
                () -> dictionaryTagService.addTag(DICTIONARY_ID, new DictionaryTagRequestDTO("travel"), OWNER));
        verifyNoInteractions(dictionaryTagRepository);
    }
}
