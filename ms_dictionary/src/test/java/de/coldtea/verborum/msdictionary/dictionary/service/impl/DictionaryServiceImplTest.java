package de.coldtea.verborum.msdictionary.dictionary.service.impl;

import de.coldtea.verborum.msdictionary.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msdictionary.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msdictionary.common.mapper.DictionaryMapper;
import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryRequestDTO;
import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryResponseDTO;
import de.coldtea.verborum.msdictionary.dictionary.entity.Dictionary;
import de.coldtea.verborum.msdictionary.dictionary.repository.DictionaryRepository;
import de.coldtea.verborum.msdictionary.tag.entity.DictionaryTag;
import de.coldtea.verborum.msdictionary.tag.repository.DictionaryTagRepository;
import de.coldtea.verborum.msdictionary.marketplacemember.repository.MarketplaceMemberRepository;
import de.coldtea.verborum.msdictionary.common.exception.SharingRequiredException;
import de.coldtea.verborum.msdictionary.word.repository.WordRepository;

import de.coldtea.verborum.msdictionary.common.event.DictionaryDeletedEvent;
import de.coldtea.verborum.msdictionary.common.event.DictionarySnapshotEvent;
import de.coldtea.verborum.msdictionary.common.event.DictionaryUpdatedEvent;
import de.coldtea.verborum.msdictionary.common.event.DictionaryVisibilityEvent;
import de.coldtea.verborum.msdictionary.common.event.OutboundEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;


import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_DELETED;
import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_SNAPSHOT;
import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_UPDATED;
import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_VISIBILITY_PRIVATE;
import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private DictionaryTagRepository dictionaryTagRepository;

    // A non-member unless a test says otherwise (existsBy... returns false), so the sharing rule is off
    @Mock
    private MarketplaceMemberRepository marketplaceMemberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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
        OutboundEvent outbound = capturedEvent();
        assertEquals(ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC, outbound.routingKey());
        DictionaryVisibilityEvent captured = (DictionaryVisibilityEvent) outbound.payload();

        DictionaryVisibilityEvent event = captured;
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
        verifyNoInteractions(eventPublisher);
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
        OutboundEvent outbound = capturedEvent();
        assertEquals(ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC, outbound.routingKey());
        DictionaryVisibilityEvent captured = (DictionaryVisibilityEvent) outbound.payload();

        DictionaryVisibilityEvent event = captured;
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
        OutboundEvent outbound = capturedEvent();
        assertEquals(ROUTING_KEY_DICTIONARY_VISIBILITY_PRIVATE, outbound.routingKey());
        DictionaryVisibilityEvent captured = (DictionaryVisibilityEvent) outbound.payload();
        assertEquals(false, captured.getIsPublic());
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
        verifyNoInteractions(eventPublisher);
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
        verifyNoInteractions(eventPublisher);
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
        assertEquals(ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC, capturedEvent().routingKey());
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
        verifyNoInteractions(eventPublisher);
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
    void getDictionaryById_AnotherUsersPublicDictionary_IsReadable() {
        // Arrange — P4-10: how an imported marketplace dictionary is opened
        String dictionaryId = "dict1";
        Dictionary theirs = Dictionary.builder().dictionaryId(dictionaryId).userId("someone-else").isPublic(true).build();
        DictionaryResponseDTO responseDTO = new DictionaryResponseDTO();
        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.of(theirs));
        when(dictionaryMapper.toDictionaryResponseDTO(theirs)).thenReturn(responseDTO);

        // Act
        DictionaryResponseDTO result = dictionaryService.getDictionaryById(dictionaryId, OWNER);

        // Assert
        assertEquals(responseDTO, result);
    }

    @Test
    void getDictionaryById_AnotherUsersPrivateDictionary_StillIs404() {
        // Arrange — explicitly private (the older test above covers a null flag)
        String dictionaryId = "dict1";
        when(dictionaryRepository.findById(dictionaryId)).thenReturn(Optional.of(
                Dictionary.builder().dictionaryId(dictionaryId).userId("someone-else").isPublic(false).build()));

        // Act & Assert
        assertThrows(RecordNotFoundException.class, () -> dictionaryService.getDictionaryById(dictionaryId, OWNER));
        verifyNoInteractions(dictionaryMapper);
    }

    @Test
    void getDictionariesByIds_IncludesOtherUsersPublicDictionaries() {
        // Arrange
        List<String> ids = List.of("mine", "their-public", "their-private");
        when(dictionaryRepository.findAllById(ids)).thenReturn(List.of(
                dictionary("mine", false),
                Dictionary.builder().dictionaryId("their-public").userId("someone-else").isPublic(true).build(),
                Dictionary.builder().dictionaryId("their-private").userId("someone-else").isPublic(false).build()));
        when(dictionaryMapper.toDictionaryResponseDTO(any(Dictionary.class))).thenReturn(new DictionaryResponseDTO());

        // Act
        List<DictionaryResponseDTO> result = dictionaryService.getDictionariesByIds(ids, OWNER);

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    void saveDictionary_OverwritingAnotherUsersPublicDictionary_IsStillForbidden() {
        // Arrange — P4-10 opens reads only; public never means writable
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(
                Dictionary.builder().dictionaryId("dict1").userId("someone-else").isPublic(true).build()));

        // Act & Assert
        assertThrows(ForbiddenOperationException.class, () -> dictionaryService.saveDictionary(requestDTO, OWNER));
        verify(dictionaryRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteDictionary_AnotherUsersPublicDictionary_IsStillForbidden() {
        // Arrange
        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(
                Dictionary.builder().dictionaryId("dict1").userId("someone-else").isPublic(true).build()));

        // Act & Assert
        assertThrows(ForbiddenOperationException.class, () -> dictionaryService.deleteDictionary("dict1", OWNER));
        verify(dictionaryRepository, never()).deleteById(any());
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

    /**
     * The service raises an OutboundEvent; OutboundEventPublisher does the actual send after commit
     * (rule 1). These tests therefore assert on what was raised, not on RabbitTemplate.
     */
    private OutboundEvent capturedEvent() {
        ArgumentCaptor<OutboundEvent> captor = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
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
        OutboundEvent outbound = capturedEvent();
        assertEquals(ROUTING_KEY_DICTIONARY_DELETED, outbound.routingKey());
        DictionaryDeletedEvent captured = (DictionaryDeletedEvent) outbound.payload();

        DictionaryDeletedEvent event = captured;
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
        verifyNoInteractions(eventPublisher);
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
        verifyNoInteractions(eventPublisher);
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

        // Assert — words must go first: they have no DB-level FK to the dictionary. Both sides use
        // a real bulk delete; deleteAllById would issue one DELETE per id (fixed 2026-07-23)
        InOrder inOrder = inOrder(wordRepository, dictionaryRepository);
        inOrder.verify(wordRepository).deleteByDictionaryIdIn(List.of("d1", "d2"));
        inOrder.verify(dictionaryRepository).deleteByDictionaryIdIn(List.of("d1", "d2"));
        verify(dictionaryRepository, never()).deleteAllById(any());
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
        verify(dictionaryRepository, never()).deleteByDictionaryIdIn(anyList());
        verifyNoInteractions(eventPublisher);
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
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void saveDictionary_PublicDictionaryRenamed_PublishesUpdatedEvent() {
        // Arrange
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary saved = dictionary("dict1", true);
        saved.setName("Renamed");

        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", true)));
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(saved);
        when(dictionaryRepository.saveAndFlush(saved)).thenReturn(saved);
        when(dictionaryMapper.toDictionaryResponseDTO(saved)).thenReturn(new DictionaryResponseDTO());

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        OutboundEvent outbound = capturedEvent();
        assertEquals(ROUTING_KEY_DICTIONARY_UPDATED, outbound.routingKey());
        DictionaryUpdatedEvent event = (DictionaryUpdatedEvent) outbound.payload();
        assertEquals("dict1", event.getDictionaryId());
        assertEquals("user1", event.getUserId());
        assertEquals("Renamed", event.getDictionaryName());
        assertEquals("EN", event.getFromLang());
        assertEquals("DE", event.getToLang());
    }

    @Test
    void saveDictionary_PublicLanguageChanged_PublishesUpdatedEvent() {
        // Arrange
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary saved = dictionary("dict1", true);
        saved.setToLang("FR");

        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", true)));
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(saved);
        when(dictionaryRepository.saveAndFlush(saved)).thenReturn(saved);
        when(dictionaryMapper.toDictionaryResponseDTO(saved)).thenReturn(new DictionaryResponseDTO());

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        OutboundEvent outbound = capturedEvent();
        assertEquals(ROUTING_KEY_DICTIONARY_UPDATED, outbound.routingKey());
        assertEquals("FR", ((DictionaryUpdatedEvent) outbound.payload()).getToLang());
    }

    @Test
    void saveDictionary_PublicRename_ComparesAgainstValuesReadBeforeTheSave() {
        // In JPA, saveAndFlush merges the new values onto the managed instance findById returned.
        // Simulated here: the "existing" row is mutated by the save. If the service compared
        // against it after saving, it would see no change and the rename would be lost
        // Arrange
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary managed = dictionary("dict1", true);
        Dictionary incoming = dictionary("dict1", true);
        incoming.setName("Renamed");

        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(managed));
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(incoming);
        when(dictionaryRepository.saveAndFlush(incoming)).thenAnswer(invocation -> {
            managed.setName(incoming.getName());
            return managed;
        });
        when(dictionaryMapper.toDictionaryResponseDTO(managed)).thenReturn(new DictionaryResponseDTO());

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        OutboundEvent outbound = capturedEvent();
        assertEquals(ROUTING_KEY_DICTIONARY_UPDATED, outbound.routingKey());
        assertEquals("Renamed", ((DictionaryUpdatedEvent) outbound.payload()).getDictionaryName());
    }

    @Test
    void saveDictionary_PrivateDictionaryRenamed_PublishesNothing() {
        // The marketplace does not list private dictionaries, so their edits are none of its business
        // Arrange
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary saved = dictionary("dict1", false);
        saved.setName("Renamed");

        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", false)));
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(saved);
        when(dictionaryRepository.saveAndFlush(saved)).thenReturn(saved);
        when(dictionaryMapper.toDictionaryResponseDTO(saved)).thenReturn(new DictionaryResponseDTO());

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void saveDictionary_GoingPublicWithRename_PublishesOnlyTheVisibilityEvent() {
        // The public event already carries the new name; an extra dictionary.updated would make the
        // marketplace apply the same change twice
        // Arrange
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary saved = dictionary("dict1", true);
        saved.setName("Renamed");

        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", false)));
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(saved);
        when(dictionaryRepository.saveAndFlush(saved)).thenReturn(saved);
        when(dictionaryMapper.toDictionaryResponseDTO(saved)).thenReturn(new DictionaryResponseDTO());

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert — capturedEvent() verifies exactly one publishEvent call
        assertEquals(ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC, capturedEvent().routingKey());
    }

    @Test
    void saveDictionary_GoingPrivateWithRename_PublishesOnlyTheVisibilityEvent() {
        // Arrange
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary saved = dictionary("dict1", false);
        saved.setName("Renamed");

        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", true)));
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(saved);
        when(dictionaryRepository.saveAndFlush(saved)).thenReturn(saved);
        when(dictionaryMapper.toDictionaryResponseDTO(saved)).thenReturn(new DictionaryResponseDTO());

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        assertEquals(ROUTING_KEY_DICTIONARY_VISIBILITY_PRIVATE, capturedEvent().routingKey());
    }

    @Test
    void publishPublicSnapshot_PublishesEveryPublicDictionary() {
        // Arrange
        Dictionary first = dictionary("dict1", true);
        Dictionary second = dictionary("dict2", true);
        second.setName("Second");
        when(dictionaryRepository.findByIsPublicTrue()).thenReturn(List.of(first, second));

        // Act
        dictionaryService.publishPublicSnapshot();

        // Assert
        OutboundEvent outbound = capturedEvent();
        assertEquals(ROUTING_KEY_DICTIONARY_SNAPSHOT, outbound.routingKey());
        DictionarySnapshotEvent event = (DictionarySnapshotEvent) outbound.payload();
        assertEquals(2, event.getDictionaries().size());
        assertEquals("dict1", event.getDictionaries().get(0).getDictionaryId());
        assertEquals("user1", event.getDictionaries().get(0).getUserId());
        assertEquals("Second", event.getDictionaries().get(1).getDictionaryName());
        assertEquals("EN", event.getDictionaries().get(1).getFromLang());
        assertEquals("DE", event.getDictionaries().get(1).getToLang());
        assertNotNull(event.getTakenAt());
    }

    @Test
    void publishPublicSnapshot_NothingPublic_StillPublishesAnEmptySnapshot() {
        // An empty snapshot is how the marketplace learns that every listing it holds is gone
        // Arrange
        when(dictionaryRepository.findByIsPublicTrue()).thenReturn(List.of());

        // Act
        dictionaryService.publishPublicSnapshot();

        // Assert
        DictionarySnapshotEvent event = (DictionarySnapshotEvent) capturedEvent().payload();
        assertTrue(event.getDictionaries().isEmpty());
    }

    @Test
    void publishPublicSnapshot_TakenAtIsReadBeforeTheQuery() {
        // A listing newer than takenAt is protected from deletion by the consumer; takenAt must not
        // postdate the query or a dictionary published mid-query could be wrongly cleared
        // Arrange
        OffsetDateTime[] queriedAt = new OffsetDateTime[1];
        when(dictionaryRepository.findByIsPublicTrue()).thenAnswer(invocation -> {
            queriedAt[0] = OffsetDateTime.now();
            return List.of();
        });

        // Act
        dictionaryService.publishPublicSnapshot();

        // Assert
        DictionarySnapshotEvent event = (DictionarySnapshotEvent) capturedEvent().payload();
        assertFalse(event.getTakenAt().isAfter(queriedAt[0]));
    }

    // ---- tags on listing events (P4-12) ----

    @Test
    void saveDictionary_NewPublicDictionary_PublicEventCarriesSortedTags() {
        // Arrange
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary dictionary = dictionary("dict1", true);
        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.empty());
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(dictionary);
        when(dictionaryRepository.saveAndFlush(dictionary)).thenReturn(dictionary);
        when(dictionaryTagRepository.findByDictionaryId("dict1")).thenReturn(List.of(tag("dict1", "travel"), tag("dict1", "food")));

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        DictionaryVisibilityEvent event = (DictionaryVisibilityEvent) capturedEvent().payload();
        assertEquals(List.of("food", "travel"), event.getTags());
    }

    @Test
    void saveDictionary_PublicDictionaryRenamed_UpdatedEventCarriesTags() {
        // Arrange
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary saved = dictionary("dict1", true);
        saved.setName("Renamed");
        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", true)));
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(saved);
        when(dictionaryRepository.saveAndFlush(saved)).thenReturn(saved);
        when(dictionaryTagRepository.findByDictionaryId("dict1")).thenReturn(List.of(tag("dict1", "food")));

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        OutboundEvent outbound = capturedEvent();
        assertEquals(ROUTING_KEY_DICTIONARY_UPDATED, outbound.routingKey());
        assertEquals(List.of("food"), ((DictionaryUpdatedEvent) outbound.payload()).getTags());
    }

    @Test
    void saveDictionary_UntaggedPublicDictionary_SendsAnEmptyListNotNull() {
        // Arrange — consumers read null as "tags unknown", so an untagged dictionary must say []
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary dictionary = dictionary("dict1", true);
        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.empty());
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(dictionary);
        when(dictionaryRepository.saveAndFlush(dictionary)).thenReturn(dictionary);

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        assertEquals(List.of(), ((DictionaryVisibilityEvent) capturedEvent().payload()).getTags());
    }

    @Test
    void publishPublicSnapshot_EachEntryCarriesItsOwnSortedTags() {
        // Arrange
        Dictionary tagged = dictionary("dict1", true);
        Dictionary untagged = dictionary("dict2", true);
        when(dictionaryRepository.findByIsPublicTrue()).thenReturn(List.of(tagged, untagged));
        when(dictionaryTagRepository.findByDictionaryIdIn(List.of("dict1", "dict2")))
                .thenReturn(List.of(tag("dict1", "travel"), tag("dict1", "food")));

        // Act
        dictionaryService.publishPublicSnapshot();

        // Assert — one tag query for the whole snapshot, not one per dictionary
        DictionarySnapshotEvent event = (DictionarySnapshotEvent) capturedEvent().payload();
        assertEquals(List.of("food", "travel"), event.getDictionaries().get(0).getTags());
        assertEquals(List.of(), event.getDictionaries().get(1).getTags());
        verify(dictionaryTagRepository, never()).findByDictionaryId(any());
    }

    @Test
    void publishTagChange_PublicDictionary_BumpsUpdatedAtAndPublishesUpdated() {
        // Arrange — without the bump the marketplace would drop the event as stale (rule 4)
        Dictionary dictionary = dictionary("dict1", true);
        OffsetDateTime before = OffsetDateTime.now().minusDays(1);
        dictionary.setUpdatedAt(before);
        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary));
        when(dictionaryRepository.saveAndFlush(dictionary)).thenReturn(dictionary);
        when(dictionaryTagRepository.findByDictionaryId("dict1")).thenReturn(List.of(tag("dict1", "food")));

        // Act
        dictionaryService.publishTagChange("dict1");

        // Assert
        verify(dictionaryRepository).saveAndFlush(dictionary);
        assertTrue(dictionary.getUpdatedAt().isAfter(before));

        OutboundEvent outbound = capturedEvent();
        assertEquals(ROUTING_KEY_DICTIONARY_UPDATED, outbound.routingKey());
        DictionaryUpdatedEvent event = (DictionaryUpdatedEvent) outbound.payload();
        assertEquals("dict1", event.getDictionaryId());
        assertEquals(List.of("food"), event.getTags());
        assertEquals(dictionary.getUpdatedAt(), event.getUpdatedAt());
    }

    @Test
    void publishTagChange_PrivateDictionary_TouchesNothing() {
        // Arrange — nothing lists it; its tags travel on the public event if it is published later
        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", false)));

        // Act
        dictionaryService.publishTagChange("dict1");

        // Assert
        verify(dictionaryRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void publishTagChange_UnknownDictionary_DoesNothing() {
        // Arrange
        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.empty());

        // Act
        dictionaryService.publishTagChange("dict1");

        // Assert
        verify(dictionaryRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    private static DictionaryTag tag(String dictionaryId, String tag) {
        return DictionaryTag.builder().tagId(dictionaryId + "-" + tag).dictionaryId(dictionaryId).tag(tag).build();
    }

    // ---- P4-16: joining/leaving shares/unshares everything; members keep one shared ----

    @Test
    void setVisibilityOfAll_Join_PublishesEveryPrivateDictionaryAndSkipsPublicOnes() {
        // Arrange
        Dictionary privateOne = dictionary("dict1", false);
        Dictionary alreadyPublic = dictionary("dict2", true);
        when(dictionaryRepository.findByUserId(OWNER)).thenReturn(List.of(privateOne, alreadyPublic));
        when(dictionaryRepository.saveAndFlush(privateOne)).thenReturn(privateOne);

        // Act
        int changed = dictionaryService.setVisibilityOfAll(OWNER, true);

        // Assert — one visibility.public, carrying the listing payload; the public one is untouched
        assertEquals(1, changed);
        assertTrue(privateOne.getIsPublic());
        verify(dictionaryRepository, never()).saveAndFlush(alreadyPublic);
        OutboundEvent outbound = capturedEvent();
        assertEquals(ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC, outbound.routingKey());
        assertEquals("dict1", ((DictionaryVisibilityEvent) outbound.payload()).getDictionaryId());
    }

    @Test
    void setVisibilityOfAll_Leave_MakesEveryPublicDictionaryPrivate() {
        // Arrange
        Dictionary first = dictionary("dict1", true);
        Dictionary second = dictionary("dict2", true);
        when(dictionaryRepository.findByUserId(OWNER)).thenReturn(List.of(first, second));
        when(dictionaryRepository.saveAndFlush(any(Dictionary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        int changed = dictionaryService.setVisibilityOfAll(OWNER, false);

        // Assert
        assertEquals(2, changed);
        assertFalse(first.getIsPublic());
        assertFalse(second.getIsPublic());
        ArgumentCaptor<OutboundEvent> captor = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .allMatch(event -> ROUTING_KEY_DICTIONARY_VISIBILITY_PRIVATE.equals(event.routingKey())));
    }

    @Test
    void saveDictionary_MemberHidesTheirLastSharedDictionary_Is400() {
        // Arrange — dict1 is public and the only shared one
        givenAMember();
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        requestDTO.setIsPublic(false);
        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", true)));
        when(dictionaryRepository.countByUserIdAndIsPublicTrueAndDictionaryIdNot(OWNER, "dict1")).thenReturn(0L);

        // Act & Assert
        assertThrows(SharingRequiredException.class, () -> dictionaryService.saveDictionary(requestDTO, OWNER));
        verify(dictionaryRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void saveDictionary_MemberHidesOneOfSeveralShared_IsAllowed() {
        // Arrange
        givenAMember();
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        requestDTO.setIsPublic(false);
        Dictionary saved = dictionary("dict1", false);
        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", true)));
        when(dictionaryRepository.countByUserIdAndIsPublicTrueAndDictionaryIdNot(OWNER, "dict1")).thenReturn(1L);
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(saved);
        when(dictionaryRepository.saveAndFlush(saved)).thenReturn(saved);

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        verify(dictionaryRepository).saveAndFlush(saved);
    }

    @Test
    void saveDictionary_MemberWithNothingSharedCreatesAPrivateOne_Is400() {
        // Arrange — they would have dictionaries but none shared
        givenAMember();
        DictionaryRequestDTO requestDTO = requestDTO("new");
        requestDTO.setIsPublic(false);
        when(dictionaryRepository.findById("new")).thenReturn(Optional.empty());
        when(dictionaryRepository.countByUserIdAndIsPublicTrueAndDictionaryIdNot(OWNER, "new")).thenReturn(0L);

        // Act & Assert
        assertThrows(SharingRequiredException.class, () -> dictionaryService.saveDictionary(requestDTO, OWNER));
    }

    @Test
    void saveDictionary_NonMemberHidesTheirLastShared_IsAllowed() {
        // Arrange — the rule is for members only
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        requestDTO.setIsPublic(false);
        Dictionary saved = dictionary("dict1", false);
        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", true)));
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(saved);
        when(dictionaryRepository.saveAndFlush(saved)).thenReturn(saved);

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        verify(dictionaryRepository).saveAndFlush(saved);
        verify(dictionaryRepository, never()).countByUserIdAndIsPublicTrueAndDictionaryIdNot(any(), any());
    }

    @Test
    void deleteDictionary_MemberDeletesTheirOnlyDictionary_IsAllowed() {
        // Arrange — afterwards they have none, which is fine
        givenAMember();
        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", true)));
        when(dictionaryRepository.countByUserIdAndDictionaryIdNot(OWNER, "dict1")).thenReturn(0L);
        when(dictionaryRepository.countByUserIdAndIsPublicTrueAndDictionaryIdNot(OWNER, "dict1")).thenReturn(0L);

        // Act
        dictionaryService.deleteDictionary("dict1", OWNER);

        // Assert
        verify(dictionaryRepository).deleteById("dict1");
    }

    @Test
    void deleteDictionary_MemberDeletesLastSharedWhilePrivateOnesRemain_Is400() {
        // Arrange — the rest would all be hidden
        givenAMember();
        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", true)));
        when(dictionaryRepository.countByUserIdAndDictionaryIdNot(OWNER, "dict1")).thenReturn(2L);
        when(dictionaryRepository.countByUserIdAndIsPublicTrueAndDictionaryIdNot(OWNER, "dict1")).thenReturn(0L);

        // Act & Assert
        assertThrows(SharingRequiredException.class, () -> dictionaryService.deleteDictionary("dict1", OWNER));
        verify(dictionaryRepository, never()).deleteById(any());
        verifyNoInteractions(wordRepository);
    }

    @Test
    void deleteDictionary_MemberDeletesAPrivateOne_IsNotChecked() {
        // Arrange — deleting a private dictionary cannot reduce what is shared
        givenAMember();
        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", false)));

        // Act
        dictionaryService.deleteDictionary("dict1", OWNER);

        // Assert
        verify(dictionaryRepository).deleteById("dict1");
        verify(dictionaryRepository, never()).countByUserIdAndIsPublicTrueAndDictionaryIdNot(any(), any());
    }

    @Test
    void deleteAllByUserId_AlsoRemovesTheMembershipCopy() {
        // Arrange
        when(dictionaryRepository.findByUserId("kc-1")).thenReturn(List.of());

        // Act
        dictionaryService.deleteAllByUserId("kc-1");

        // Assert
        verify(marketplaceMemberRepository).deleteById("kc-1");
    }

    @Test
    void saveDictionary_MemberRenamesAnAlreadyPrivateDictionary_IsNotChecked() {
        // Arrange — staying private takes nothing shared away, even if nothing else is shared
        givenAMember();
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        requestDTO.setIsPublic(false);
        Dictionary saved = dictionary("dict1", false);
        saved.setName("Renamed");
        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", false)));
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(saved);
        when(dictionaryRepository.saveAndFlush(saved)).thenReturn(saved);

        // Act
        dictionaryService.saveDictionary(requestDTO, OWNER);

        // Assert
        verify(dictionaryRepository).saveAndFlush(saved);
        verify(dictionaryRepository, never()).countByUserIdAndIsPublicTrueAndDictionaryIdNot(any(), any());
    }

    private void givenAMember() {
        when(marketplaceMemberRepository.existsByKeycloakIdAndIsMemberTrue(OWNER)).thenReturn(true);
    }
}

