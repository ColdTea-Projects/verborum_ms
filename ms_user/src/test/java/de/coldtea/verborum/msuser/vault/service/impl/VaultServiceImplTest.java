package de.coldtea.verborum.msuser.vault.service.impl;

import de.coldtea.verborum.msuser.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msuser.common.event.OutboundEvent;
import de.coldtea.verborum.msuser.common.event.VaultChangedEvent;
import de.coldtea.verborum.msuser.common.mapper.VaultEntryMapper;
import de.coldtea.verborum.msuser.common.utils.RequestDevice;
import de.coldtea.verborum.msuser.user.entity.User;
import de.coldtea.verborum.msuser.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msuser.user.repository.UserRepository;
import de.coldtea.verborum.msuser.vault.dto.VaultEntryRequestDTO;
import de.coldtea.verborum.msuser.vault.dto.VaultEntryResponseDTO;
import de.coldtea.verborum.msuser.vault.entity.VaultEntry;
import de.coldtea.verborum.msuser.vault.repository.VaultEntryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static de.coldtea.verborum.msuser.common.config.RabbitMQConfig.ROUTING_KEY_VAULT_CHANGED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VaultServiceImplTest {

    private static final String USER_ID = "1";
    private static final String DICTIONARY_ID = "2";
    /** The JWT subject of the caller — in ms_user that is the profile's keycloakId (P3-05). */
    private static final String CALLER_KC_ID = "kc-1";

    @Mock
    private VaultEntryRepository vaultEntryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VaultEntryMapper vaultEntryMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private RequestDevice requestDevice;

    @InjectMocks
    private VaultServiceImpl vaultService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Every caller-facing vault method resolves the profile to check ownership, so the default
        // fixture is "the vault belongs to the caller, a Forum member". The forbidden and non-member
        // cases re-stub this.
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(User.builder().userId(USER_ID).keycloakId(CALLER_KC_ID)
                        .marketplaceAgreementAccepted(true).build()));
    }

    @Test
    void getVaultEntriesByUser_Success() {
        // Arrange
        VaultEntry vaultEntry = new VaultEntry();
        VaultEntryResponseDTO responseDTO = new VaultEntryResponseDTO();

        when(vaultEntryRepository.findByUserId(USER_ID)).thenReturn(List.of(vaultEntry));
        when(vaultEntryMapper.toVaultEntryResponseDTO(vaultEntry)).thenReturn(responseDTO);

        // Act
        List<VaultEntryResponseDTO> result = vaultService.getVaultEntriesByUser(USER_ID, CALLER_KC_ID);

        // Assert
        assertEquals(List.of(responseDTO), result);
        verify(vaultEntryRepository).findByUserId(USER_ID);
        verify(vaultEntryMapper).toVaultEntryResponseDTO(vaultEntry);
    }

    @Test
    void getVaultEntriesByUser_EmptyVault() {
        // Arrange
        when(vaultEntryRepository.findByUserId(USER_ID)).thenReturn(List.of());

        // Act
        List<VaultEntryResponseDTO> result = vaultService.getVaultEntriesByUser(USER_ID, CALLER_KC_ID);

        // Assert
        assertEquals(List.of(), result);
        verifyNoInteractions(vaultEntryMapper);
    }

    @Test
    void getVaultEntriesByUser_NotAMember_ReturnsEmptyWithoutReading() {
        // Arrange — never joined (null) or withdrew (false): the rows exist but stay hidden
        for (Boolean accepted : new Boolean[]{false, null}) {
            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.of(User.builder().userId(USER_ID).keycloakId(CALLER_KC_ID)
                            .marketplaceAgreementAccepted(accepted).build()));

            // Act
            List<VaultEntryResponseDTO> result = vaultService.getVaultEntriesByUser(USER_ID, CALLER_KC_ID);

            // Assert
            assertEquals(List.of(), result);
        }
        verifyNoInteractions(vaultEntryRepository, vaultEntryMapper);
    }

    @Test
    void importDictionary_ResolvesKeycloakIdToUserId() {
        // Arrange — the event carries keycloakId; fk_user_id must be ms_user's own user_id
        String keycloakId = "kc-1";
        User user = User.builder().userId(USER_ID).keycloakId(keycloakId).build();
        VaultEntry vaultEntry = new VaultEntry();
        VaultEntryResponseDTO responseDTO = new VaultEntryResponseDTO();

        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
        when(vaultEntryRepository.findByUserIdAndDictionaryId(USER_ID, DICTIONARY_ID)).thenReturn(Optional.empty());
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(vaultEntryMapper.toVaultEntry(anyString(), eq(USER_ID), any(VaultEntryRequestDTO.class)))
                .thenReturn(vaultEntry);
        when(vaultEntryRepository.saveAndFlush(vaultEntry)).thenReturn(vaultEntry);
        when(vaultEntryMapper.toVaultEntryResponseDTO(vaultEntry)).thenReturn(responseDTO);

        // Act
        VaultEntryResponseDTO result = vaultService.importDictionary(keycloakId, DICTIONARY_ID);

        // Assert
        assertEquals(responseDTO, result);
        verify(vaultEntryRepository).findByUserIdAndDictionaryId(USER_ID, DICTIONARY_ID);
        verify(vaultEntryRepository).saveAndFlush(vaultEntry);
    }

    @Test
    void importDictionary_Redelivered_DoesNotDuplicate() {
        // Arrange — a redelivered dictionary.imported must not create a second vault entry
        String keycloakId = "kc-1";
        User user = User.builder().userId(USER_ID).keycloakId(keycloakId).build();
        VaultEntry existingEntry = new VaultEntry();
        VaultEntryResponseDTO responseDTO = new VaultEntryResponseDTO();

        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
        when(vaultEntryRepository.findByUserIdAndDictionaryId(USER_ID, DICTIONARY_ID))
                .thenReturn(Optional.of(existingEntry));
        when(vaultEntryMapper.toVaultEntryResponseDTO(existingEntry)).thenReturn(responseDTO);

        // Act
        VaultEntryResponseDTO result = vaultService.importDictionary(keycloakId, DICTIONARY_ID);

        // Assert
        assertEquals(responseDTO, result);
        verify(vaultEntryRepository, never()).saveAndFlush(any());
    }

    // ---- vault.changed (P4N-14) ----

    private VaultChangedEvent publishedVaultChange() {
        ArgumentCaptor<OutboundEvent> captor = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(ROUTING_KEY_VAULT_CHANGED, captor.getValue().routingKey());
        return (VaultChangedEvent) captor.getValue().payload();
    }

    @Test
    void importDictionary_NewEntry_PublishesVaultChangedWithTheImportingDevice() {
        // Arrange
        User user = User.builder().userId(USER_ID).keycloakId("kc-1").build();
        when(userRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(user));
        when(vaultEntryRepository.findByUserIdAndDictionaryId(USER_ID, DICTIONARY_ID)).thenReturn(Optional.empty());
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        VaultEntry vaultEntry = new VaultEntry();
        when(vaultEntryMapper.toVaultEntry(anyString(), eq(USER_ID), any(VaultEntryRequestDTO.class))).thenReturn(vaultEntry);
        when(vaultEntryRepository.saveAndFlush(vaultEntry)).thenReturn(vaultEntry);
        when(vaultEntryMapper.toVaultEntryResponseDTO(vaultEntry)).thenReturn(new VaultEntryResponseDTO());

        // Act
        vaultService.importDictionary("kc-1", DICTIONARY_ID, "device-1");

        // Assert — keyed on the JWT subject, with the install to skip; ids only
        VaultChangedEvent event = publishedVaultChange();
        assertEquals("kc-1", event.getKeycloakId());
        assertEquals(DICTIONARY_ID, event.getDictionaryId());
        assertEquals("device-1", event.getDeviceId());
    }

    @Test
    void importDictionary_Redelivered_PublishesNothing() {
        // Arrange — the entry is already there: a redelivered import is a no-op and says nothing
        User user = User.builder().userId(USER_ID).keycloakId("kc-1").build();
        when(userRepository.findByKeycloakId("kc-1")).thenReturn(Optional.of(user));
        when(vaultEntryRepository.findByUserIdAndDictionaryId(USER_ID, DICTIONARY_ID))
                .thenReturn(Optional.of(new VaultEntry()));
        when(vaultEntryMapper.toVaultEntryResponseDTO(any())).thenReturn(new VaultEntryResponseDTO());

        // Act
        vaultService.importDictionary("kc-1", DICTIONARY_ID, "device-1");

        // Assert
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deleteVaultEntry_Existing_PublishesVaultChangedWithTheRequestDevice() {
        // Arrange
        when(vaultEntryRepository.findByUserIdAndDictionaryId(USER_ID, DICTIONARY_ID))
                .thenReturn(Optional.of(new VaultEntry()));
        when(requestDevice.currentDeviceId()).thenReturn("device-9");

        // Act
        vaultService.deleteVaultEntry(USER_ID, DICTIONARY_ID, CALLER_KC_ID);

        // Assert
        VaultChangedEvent event = publishedVaultChange();
        assertEquals(CALLER_KC_ID, event.getKeycloakId());
        assertEquals("device-9", event.getDeviceId());
    }

    @Test
    void deleteVaultEntry_NotInTheVault_PublishesNothing() {
        // Arrange
        when(vaultEntryRepository.findByUserIdAndDictionaryId(USER_ID, DICTIONARY_ID)).thenReturn(Optional.empty());

        // Act — a no-op 200, as before
        vaultService.deleteVaultEntry(USER_ID, DICTIONARY_ID, CALLER_KC_ID);

        // Assert
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void importDictionary_UnknownKeycloakId() {
        // Arrange
        when(userRepository.findByKeycloakId("kc-unknown")).thenReturn(Optional.empty());

        // Act & Assert — throws so the listener dead-letters instead of silently dropping
        assertThrows(RecordNotFoundException.class, () -> vaultService.importDictionary("kc-unknown", DICTIONARY_ID));
        verifyNoInteractions(vaultEntryRepository, vaultEntryMapper);
    }

    @Test
    void deleteVaultEntry_Success() {
        // Act
        vaultService.deleteVaultEntry(USER_ID, DICTIONARY_ID, CALLER_KC_ID);

        // Assert
        verify(vaultEntryRepository).deleteByUserIdAndDictionaryId(USER_ID, DICTIONARY_ID);
    }

    @Test
    void getVaultEntriesByUser_AnotherUsersVault_Is404() {
        // Arrange
        givenTheVaultBelongsToSomeoneElse();

        // Act & Assert
        assertThrows(RecordNotFoundException.class,
                () -> vaultService.getVaultEntriesByUser(USER_ID, CALLER_KC_ID));
        verifyNoInteractions(vaultEntryRepository);
    }

    @Test
    void deleteVaultEntry_AnotherUsersVault_IsForbidden() {
        // Arrange
        givenTheVaultBelongsToSomeoneElse();

        // Act & Assert
        assertThrows(ForbiddenOperationException.class,
                () -> vaultService.deleteVaultEntry(USER_ID, DICTIONARY_ID, CALLER_KC_ID));
        verifyNoInteractions(vaultEntryRepository);
    }

    /** importDictionary is the event path and takes no caller, so it stays reachable — by design. */
    @Test
    void importDictionary_IsNotOwnershipChecked() {
        // Arrange
        String keycloakId = "kc-someone-else";
        User user = User.builder().userId("other").keycloakId(keycloakId).build();

        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
        when(vaultEntryRepository.findByUserIdAndDictionaryId("other", DICTIONARY_ID))
                .thenReturn(Optional.of(new VaultEntry()));
        when(vaultEntryMapper.toVaultEntryResponseDTO(any())).thenReturn(new VaultEntryResponseDTO());

        // Act & Assert — no exception: ms_marketplace, not a user, is the actor here
        assertDoesNotThrow(() -> vaultService.importDictionary(keycloakId, DICTIONARY_ID));
    }

    private void givenTheVaultBelongsToSomeoneElse() {
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(User.builder().userId(USER_ID).keycloakId("kc-someone-else").build()));
    }
}
