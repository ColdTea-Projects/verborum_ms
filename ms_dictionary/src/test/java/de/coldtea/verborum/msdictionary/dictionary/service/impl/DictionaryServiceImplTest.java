package de.coldtea.verborum.msdictionary.dictionary.service.impl;

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
    void saveDictionary_NewPublicDictionary_PublishesPublicEvent() {
        // Arrange
        DictionaryRequestDTO requestDTO = requestDTO("dict1");
        Dictionary dictionary = dictionary("dict1", true);

        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.empty());
        when(dictionaryMapper.toDictionary(requestDTO)).thenReturn(dictionary);
        when(dictionaryRepository.saveAndFlush(dictionary)).thenReturn(dictionary);
        when(dictionaryMapper.toDictionaryResponseDTO(dictionary)).thenReturn(new DictionaryResponseDTO());

        // Act
        dictionaryService.saveDictionary(requestDTO);

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
        dictionaryService.saveDictionary(requestDTO);

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
        dictionaryService.saveDictionary(requestDTO);

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
        dictionaryService.saveDictionary(requestDTO);

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
        dictionaryService.saveDictionary(requestDTO);

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
        dictionaryService.saveDictionary(requestDTO);

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
        dictionaryService.saveDictionary(requestDTO);

        // Assert
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE),
                eq(ROUTING_KEY_DICTIONARY_VISIBILITY_PUBLIC), any(DictionaryVisibilityEvent.class));
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
        dictionaryService.deleteDictionary(dictionaryId);

        // Assert
        verify(wordRepository).deleteByDictionaryIdIn(List.of(dictionaryId));
        verify(dictionaryRepository).deleteById(dictionaryId);
    }

    @Test
    void deleteDictionary_PublishesDeletedEvent() {
        // Arrange
        when(dictionaryRepository.findById("dict1")).thenReturn(Optional.of(dictionary("dict1", true)));

        // Act
        dictionaryService.deleteDictionary("dict1");

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
        dictionaryService.deleteDictionary("dict1");

        // Assert
        verifyNoInteractions(rabbitTemplate);
        verify(wordRepository).deleteByDictionaryIdIn(List.of("dict1"));
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
