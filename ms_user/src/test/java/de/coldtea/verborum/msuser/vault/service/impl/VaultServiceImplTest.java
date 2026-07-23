package de.coldtea.verborum.msuser.vault.service.impl;

import de.coldtea.verborum.msuser.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msuser.common.mapper.VaultEntryMapper;
import de.coldtea.verborum.msuser.user.entity.User;
import de.coldtea.verborum.msuser.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msuser.user.repository.UserRepository;
import de.coldtea.verborum.msuser.vault.dto.VaultEntryRequestDTO;
import de.coldtea.verborum.msuser.vault.dto.VaultEntryResponseDTO;
import de.coldtea.verborum.msuser.vault.entity.VaultEntry;
import de.coldtea.verborum.msuser.vault.repository.VaultEntryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    @InjectMocks
    private VaultServiceImpl vaultService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Every caller-facing vault method resolves the profile to check ownership, so the default
        // fixture is "the vault belongs to the caller". The forbidden cases re-stub this.
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(User.builder().userId(USER_ID).keycloakId(CALLER_KC_ID).build()));
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
    void addVaultEntry_Success() {
        // Arrange
        VaultEntryRequestDTO requestDTO = new VaultEntryRequestDTO(DICTIONARY_ID);
        VaultEntry vaultEntry = new VaultEntry();
        VaultEntryResponseDTO responseDTO = new VaultEntryResponseDTO();

        when(vaultEntryRepository.findByUserIdAndDictionaryId(USER_ID, DICTIONARY_ID)).thenReturn(Optional.empty());
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(vaultEntryMapper.toVaultEntry(anyString(), eq(USER_ID), eq(requestDTO))).thenReturn(vaultEntry);
        when(vaultEntryRepository.saveAndFlush(vaultEntry)).thenReturn(vaultEntry);
        when(vaultEntryMapper.toVaultEntryResponseDTO(vaultEntry)).thenReturn(responseDTO);

        // Act
        VaultEntryResponseDTO result = vaultService.addVaultEntry(USER_ID, requestDTO, CALLER_KC_ID);

        // Assert
        assertEquals(responseDTO, result);
        verify(vaultEntryRepository).saveAndFlush(vaultEntry);
    }

    @Test
    void addVaultEntry_AlreadyInVault_ReturnsExistingWithoutSaving() {
        // Arrange
        VaultEntryRequestDTO requestDTO = new VaultEntryRequestDTO(DICTIONARY_ID);
        VaultEntry existingEntry = new VaultEntry();
        VaultEntryResponseDTO responseDTO = new VaultEntryResponseDTO();

        when(vaultEntryRepository.findByUserIdAndDictionaryId(USER_ID, DICTIONARY_ID))
                .thenReturn(Optional.of(existingEntry));
        when(vaultEntryMapper.toVaultEntryResponseDTO(existingEntry)).thenReturn(responseDTO);

        // Act
        VaultEntryResponseDTO result = vaultService.addVaultEntry(USER_ID, requestDTO, CALLER_KC_ID);

        // Assert
        assertEquals(responseDTO, result);
        verify(vaultEntryRepository, never()).saveAndFlush(any());
        verify(userRepository, never()).existsById(anyString());
    }

    @Test
    void addVaultEntry_UnknownUser() {
        // Arrange
        VaultEntryRequestDTO requestDTO = new VaultEntryRequestDTO(DICTIONARY_ID);

        when(vaultEntryRepository.findByUserIdAndDictionaryId(USER_ID, DICTIONARY_ID)).thenReturn(Optional.empty());
        when(userRepository.existsById(USER_ID)).thenReturn(false);

        // Act & Assert
        assertThrows(RecordNotFoundException.class, () -> vaultService.addVaultEntry(USER_ID, requestDTO, CALLER_KC_ID));
        verify(vaultEntryRepository, never()).saveAndFlush(any());
        verifyNoInteractions(vaultEntryMapper);
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
    void getVaultEntriesByUser_AnotherUsersVault_IsForbidden() {
        // Arrange
        givenTheVaultBelongsToSomeoneElse();

        // Act & Assert
        assertThrows(ForbiddenOperationException.class,
                () -> vaultService.getVaultEntriesByUser(USER_ID, CALLER_KC_ID));
        verifyNoInteractions(vaultEntryRepository);
    }

    @Test
    void addVaultEntry_AnotherUsersVault_IsForbidden() {
        // Arrange
        givenTheVaultBelongsToSomeoneElse();

        // Act & Assert
        assertThrows(ForbiddenOperationException.class,
                () -> vaultService.addVaultEntry(USER_ID, new VaultEntryRequestDTO(DICTIONARY_ID), CALLER_KC_ID));
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
