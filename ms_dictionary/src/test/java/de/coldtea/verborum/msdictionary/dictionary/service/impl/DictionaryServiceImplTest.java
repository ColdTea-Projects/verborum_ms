package de.coldtea.verborum.msdictionary.dictionary.service.impl;

import de.coldtea.verborum.msdictionary.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msdictionary.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msdictionary.common.mapper.DictionaryMapper;
import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryRequestDTO;
import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryResponseDTO;
import de.coldtea.verborum.msdictionary.dictionary.entity.Dictionary;
import de.coldtea.verborum.msdictionary.dictionary.repository.DictionaryRepository;
import de.coldtea.verborum.msdictionary.word.repository.WordRepository;

import de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig;
import de.coldtea.verborum.msdictionary.common.event.DictionaryDeletedEvent;
import de.coldtea.verborum.msdictionary.common.event.DictionaryVisibilityEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;


import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_DELETED;
import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_VISIBILITY_PRIVATE;
import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class DictionaryServiceImplTest {

    /** The JWT subject of the caller. Matches the fixture dictionaries' userId (P3-05). */
    private static final String OWNER = "user1";

    @Mock
    private DictionaryRepository dictionaryRepository;

    @Mock
    private WordRepository wordRepository;

    @Mock
    private DictionaryMapper dictionaryMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

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
        DictionaryResponseDTO result = dictionaryService.saveDictionary(requestDTO, OWNER);

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
        assertThrows(RuntimeException.class, () -> dictionaryService.saveDictionary(requestDTO, OWNER));
        verify(dictionaryMapper).toDictionary(requestDTO);
        verify(dictionaryRepository).saveAndFlush(dictionary);
        verifyNoMoreInteractions(dictionaryMapper);
    }

    @Test
    void saveDictionary_NewPublicDictionary_PublishesPublicEvent() {
        // Arrange
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary dictionary = dictionary("dict1", true);

        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.empty());
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(dictionary);
        when(dictionaryRepository.saveAndFlush(dictionary)).thenReturn(dictionary);
        when(dictionaryMapper.toDictionaryResponseDTO(dictionary)).thenReturn(new DictionaryResponseDTO());

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        ArgumentCaptor<DictionaryVisibilityEvent> captor = ArgumentCaptor.forClass(DictionaryVisibilityEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE),
                eq(ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC), captor.capture());

        DictionaryVisibilityEvent event = captor.getValue();
        assertEquals("dict1", event.getDictionaryId());
        assertEquals("user1", event.getUserId());
        assertEquals("Test Dictionary", event.getDictionaryName());
        assertEquals("EN", event.getFromLang());
        assertEquals("DE", event.getToLang());
        assertTrue(event.getIsPublic());
    }

    @Test
    void saveDictionary_NewPrivateDictionary_PublishesNothing() {
        // Arrange
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary dictionary = dictionary("dict1", false);

        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.empty());
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(dictionary);
        when(dictionaryRepository.saveAndFlush(dictionary)).thenReturn(dictionary);
        when(dictionaryMapper.toDictionaryResponseDTO(dictionary)).thenReturn(new DictionaryResponseDTO());

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void saveDictionary_PrivateToPublic_PublishesPublicEvent() {
        // Arrange
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary saved = dictionary("dict1", true);

        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", false)));
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(saved);
        when(dictionaryRepository.saveAndFlush(saved)).thenReturn(saved);
        when(dictionaryMapper.toDictionaryResponseDTO(saved)).thenReturn(new DictionaryResponseDTO());

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        ArgumentCaptor<DictionaryVisibilityEvent> captor = ArgumentCaptor.forClass(DictionaryVisibilityEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE),
                eq(ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC), captor.capture());

        DictionaryVisibilityEvent event = captor.getValue();
        assertEquals("dict1", event.getDictionaryId());
        assertEquals("user1", event.getUserId());
        assertTrue(event.getIsPublic());
    }

    @Test
    void saveDictionary_PublicToPrivate_PublishesPrivateEvent() {
        // Arrange
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary saved = dictionary("dict1", false);

        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", true)));
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(saved);
        when(dictionaryRepository.saveAndFlush(saved)).thenReturn(saved);
        when(dictionaryMapper.toDictionaryResponseDTO(saved)).thenReturn(new DictionaryResponseDTO());

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        ArgumentCaptor<DictionaryVisibilityEvent> captor = ArgumentCaptor.forClass(DictionaryVisibilityEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE),
                eq(ROUTING_KEY_DICTIONARY_VISIBILITY_PRIVATE), captor.capture());
        assertEquals(false, captor.getValue().getIsPublic());
    }

    @Test
    void saveDictionary_PublicDictionaryResaved_PublishesNothing() {
        // A rename of an already-public dictionary must not re-announce it — ms_marketplace
        // would create a second listing for the same dictionary
        // Arrange
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary saved = dictionary("dict1", true);

        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", true)));
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(saved);
        when(dictionaryRepository.saveAndFlush(saved)).thenReturn(saved);
        when(dictionaryMapper.toDictionaryResponseDTO(saved)).thenReturn(new DictionaryResponseDTO());

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void saveDictionary_PrivateDictionaryResaved_PublishesNothing() {
        // Arrange
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary saved = dictionary("dict1", false);

        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", false)));
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(saved);
        when(dictionaryRepository.saveAndFlush(saved)).thenReturn(saved);
        when(dictionaryMapper.toDictionaryResponseDTO(saved)).thenReturn(new DictionaryResponseDTO());

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void saveDictionary_StoredVisibilityIsNull_TreatedAsPrivate() {
        // A row with a null is_public must not be read as "was public" — going public from null
        // has to still announce itself
        // Arrange
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary saved = dictionary("dict1", true);

        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", null)));
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(saved);
        when(dictionaryRepository.saveAndFlush(saved)).thenReturn(saved);
        when(dictionaryMapper.toDictionaryResponseDTO(saved)).thenReturn(new DictionaryResponseDTO());

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE),
                eq(ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC), any(DictionaryVisibilityEvent.class));
    }

    @Test
    void deleteDictionary_AnotherUsersDictionary_IsForbidden() {
        // Arrange — before P3-08 this deleted the other user's dictionary and its words outright
        String dictionaryId = "dict1";
        when(dictionaryRepository.findById(dictionaryId))
                .thenReturn(Optional.of(Dictionary.builder().dictionaryId(dictionaryId).userId("someone-else").build()));

        // Act & Assert
        assertThrows(ForbiddenOperationException.class,
                () -> dictionaryService.deleteDictionary(dictionaryId, OWNER));
        verify(dictionaryRepository, never()).deleteById(anyString());
        verify(wordRepository, never()).deleteByDictionaryIdIn(any());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void getDictionaryById_AnotherUsersDictionary_Is404NotForbidden() {
        // Arrange — 404 on purpose: a 403 would confirm the id exists
        String dictionaryId = "dict1";
        when(dictionaryRepository.findById(dictionaryId))
                .thenReturn(Optional.of(Dictionary.builder().dictionaryId(dictionaryId).userId("someone-else").build()));

        // Act & Assert
        assertThrows(RecordNotFoundException.class,
                () -> dictionaryService.getDictionaryById(dictionaryId, OWNER));
        verifyNoInteractions(dictionaryMapper);
    }

    @Test
    void getDictionariesByIds_FiltersOutOtherUsers() {
        // Arrange
        List<String> ids = List.of("mine", "theirs");
        when(dictionaryRepository.findAllById(ids)).thenReturn(List.of(
                dictionary("mine", false),
                Dictionary.builder().dictionaryId("theirs").userId("someone-else").build()));
        when(dictionaryMapper.toDictionaryResponseDTO(any(Dictionary.class))).thenReturn(new DictionaryResponseDTO());

        // Act
        List<DictionaryResponseDTO> result = dictionaryService.getDictionariesByIds(ids, OWNER);

        // Assert — dropped silently rather than refused, so the response cannot be used to probe ids
        assertEquals(1, result.size());
        verify(dictionaryMapper, times(1)).toDictionaryResponseDTO(any(Dictionary.class));
    }

    private static DictionaryRequestDTO requestDTO(String dictionaryId) {
        DictionaryRequestDTO requestDTO = new DictionaryRequestDTO();
        requestDTO.setDictionaryId(dictionaryId);
        return requestDTO;
    }

    private static Dictionary dictionary(String dictionaryId, Boolean isPublic) {
        return Dictionary.builder()
                .dictionaryId(dictionaryId)
                .userId("user1")
                .name("Test Dictionary")
                .isPublic(isPublic)
                .fromLang("EN")
                .toLang("DE")
                .build();
    }

    @Test
    void deleteDictionary_Success() {
        // Arrange
        String dictionaryId = "1";

        // Act
        dictionaryService.deleteDictionary(dictionaryId, OWNER);

        // Assert
        verify(wordRepository).deleteByDictionaryIdIn(List.of(dictionaryId));
        verify(dictionaryRepository).deleteById(dictionaryId);
    }

    @Test
    void deleteDictionary_PublishesDeletedEvent() {
        // Arrange
        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", true)));

        // Act
        dictionaryService.deleteDictionary("dict1", OWNER);

        // Assert
        ArgumentCaptor<DictionaryDeletedEvent> captor = ArgumentCaptor.forClass(DictionaryDeletedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE),
                eq(ROUTING_KEY_DICTIONARY_DELETED), captor.capture());

        DictionaryDeletedEvent event = captor.getValue();
        assertEquals("dict1", event.getDictionaryId());
        assertEquals("user1", event.getUserId());
        verify(wordRepository).deleteByDictionaryIdIn(List.of("dict1"));
        verify(dictionaryRepository).deleteById("dict1");
    }

    @Test
    void deleteDictionary_UnknownDictionary_PublishesNothingButStillCleansUpWords() {
        // Deleting something that was never there must not announce a deletion. The word cleanup
        // must still run though — words outlive a missing dictionary row (no FK), and this is
        // what removes them
        // Arrange
        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.empty());

        // Act
        dictionaryService.deleteDictionary("dict1", OWNER);

        // Assert
        verifyNoInteractions(rabbitTemplate);
        verify(wordRepository).deleteByDictionaryIdIn(List.of("dict1"));
    }

    @Test
    void getDictionaryById_Success() {
        // Arrange
        String dictionaryId = "1";
        Dictionary dictionary = dictionary(dictionaryId, false);
        DictionaryResponseDTO responseDTO = new DictionaryResponseDTO();

        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.of(dictionary));
        when(dictionaryMapper.toDictionaryResponseDTO(dictionary)).thenReturn(responseDTO);

        // Act
        DictionaryResponseDTO result = dictionaryService.getDictionaryById(dictionaryId, OWNER);

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
        assertThrows(RecordNotFoundException.class, () -> dictionaryService.getDictionaryById(dictionaryId, OWNER));
        verifyNoInteractions(dictionaryMapper);
    }

    @Test
    void getDictionariesByIds_Success() {
        // Arrange
        List<String> dictionaryIds = List.of("1", "2");
        // both belong to the caller — ids owned by anyone else are filtered out (P3-08)
        List<Dictionary> dictionaries = Arrays.asList(dictionary("1", false), dictionary("2", false));

        when(dictionaryRepository.findAllById(dictionaryIds)).thenReturn(dictionaries);
        when(dictionaryMapper.toDictionaryResponseDTO(any(Dictionary.class))).thenReturn(new DictionaryResponseDTO());

        // Act
        List<DictionaryResponseDTO> result = dictionaryService.getDictionariesByIds(dictionaryIds, OWNER);

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
        List<DictionaryResponseDTO> result = dictionaryService.getDictionariesByIds(dictionaryIds, OWNER);

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
    }

    @Test
    void saveDictionary_BodyNamingAnotherUser_IsForbidden() {
        // Arrange — a client that sends the wrong owner must fail loudly, not have it rewritten
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        requestDTO.setUserId("someone-else");

        // Act & Assert
        assertThrows(ForbiddenOperationException.class, () -> dictionaryService.saveDictionary(requestDTO, OWNER));
        verify(dictionaryRepository, never()).saveAndFlush(any());
    }

    @Test
    void saveDictionary_OverwritingAnotherUsersDictionary_IsForbidden() {
        // Arrange — the client supplies dictionaryId, so a POST could otherwise take over a row
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary someoneElses = Dictionary.builder().dictionaryId("dict1").userId("someone-else").build();

        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(someoneElses));

        // Act & Assert
        assertThrows(ForbiddenOperationException.class, () -> dictionaryService.saveDictionary(requestDTO, OWNER));
        verify(dictionaryRepository, never()).saveAndFlush(any());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void saveDictionary_OwnerComesFromTheToken() {
        // Arrange — even with no userId in the body, the stored row belongs to the caller
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary mapped = Dictionary.builder().dictionaryId("dict1").build();

        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.empty());
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(mapped);
        when(dictionaryRepository.saveAndFlush(mapped)).thenReturn(mapped);
        when(dictionaryMapper.toDictionaryResponseDTO(mapped)).thenReturn(new DictionaryResponseDTO());

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        ArgumentCaptor<Dictionary> captor = ArgumentCaptor.forClass(Dictionary.class);
        verify(dictionaryRepository).saveAndFlush(captor.capture());
        assertEquals(OWNER, captor.getValue().getUserId());
    }

    @Test
    void deleteAllByUserId_DeletesWordsThenDictionaries() {
        // Arrange — userId here is the JWT subject (the event's keycloakId)
        String keycloakId = "kc-1";
        Dictionary first = Dictionary.builder().dictionaryId("d1").userId(keycloakId).build();
        Dictionary second = Dictionary.builder().dictionaryId("d2").userId(keycloakId).build();

        when(dictionaryRepository.findByUserId(keycloakId)).thenReturn(List.of(first, second));

        // Act
        dictionaryService.deleteAllByUserId(keycloakId);

        // Assert — words must go first: they have no DB-level FK to the dictionary
        InOrder inOrder = inOrder(wordRepository, dictionaryRepository);
        inOrder.verify(wordRepository).deleteByDictionaryIdIn(List.of("d1", "d2"));
        inOrder.verify(dictionaryRepository).deleteAllById(List.of("d1", "d2"));
    }

    @Test
    void deleteAllByUserId_NoDictionaries_IsANoOp() {
        // Arrange — also the redelivery case: the second delivery finds nothing left
        String keycloakId = "kc-unknown";
        when(dictionaryRepository.findByUserId(keycloakId)).thenReturn(List.of());

        // Act
        dictionaryService.deleteAllByUserId(keycloakId);

        // Assert — an empty IN (...) delete would be pointless, and must not blow up
        verify(wordRepository, never()).deleteByDictionaryIdIn(anyList());
        verify(dictionaryRepository, never()).deleteAllById(anyList());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void deleteAllByUserId_PublishesNothing() {
        // Arrange — ms_marketplace consumes user.deleted itself, so re-announcing each dictionary
        // would duplicate work it is already doing
        String keycloakId = "kc-1";
        when(dictionaryRepository.findByUserId(keycloakId))
                .thenReturn(List.of(Dictionary.builder().dictionaryId("d1").userId(keycloakId).build()));

        // Act
        dictionaryService.deleteAllByUserId(keycloakId);

        // Assert
        verifyNoInteractions(rabbitTemplate);
    }
}
