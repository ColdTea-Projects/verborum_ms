package de.coldtea.verborum.msuser.vault.service.impl;

import de.coldtea.verborum.msuser.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msuser.common.mapper.VaultEntryMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VaultServiceImplTest {

    private static final String USER_ID = "1";
    private static final String DICTIONARY_ID = "2";

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
    }

    @Test
    void getVaultEntriesByUser_Success() {
        // Arrange
        VaultEntry vaultEntry = new VaultEntry();
        VaultEntryResponseDTO responseDTO = new VaultEntryResponseDTO();

        when(vaultEntryRepository.findByUserId(USER_ID)).thenReturn(List.of(vaultEntry));
        when(vaultEntryMapper.toVaultEntryResponseDTO(vaultEntry)).thenReturn(responseDTO);

        // Act
        List<VaultEntryResponseDTO> result = vaultService.getVaultEntriesByUser(USER_ID);

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
        List<VaultEntryResponseDTO> result = vaultService.getVaultEntriesByUser(USER_ID);

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
        VaultEntryResponseDTO result = vaultService.addVaultEntry(USER_ID, requestDTO);

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
        VaultEntryResponseDTO result = vaultService.addVaultEntry(USER_ID, requestDTO);

        // Assert
        assertEquals(responseDTO, result);
        verify(vaultEntryRepository, never()).saveAndFlush(any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void addVaultEntry_UnknownUser() {
        // Arrange
        VaultEntryRequestDTO requestDTO = new VaultEntryRequestDTO(DICTIONARY_ID);

        when(vaultEntryRepository.findByUserIdAndDictionaryId(USER_ID, DICTIONARY_ID)).thenReturn(Optional.empty());
        when(userRepository.existsById(USER_ID)).thenReturn(false);

        // Act & Assert
        assertThrows(RecordNotFoundException.class, () -> vaultService.addVaultEntry(USER_ID, requestDTO));
        verify(vaultEntryRepository, never()).saveAndFlush(any());
        verifyNoInteractions(vaultEntryMapper);
    }

    @Test
    void deleteVaultEntry_Success() {
        // Act
        vaultService.deleteVaultEntry(USER_ID, DICTIONARY_ID);

        // Assert
        verify(vaultEntryRepository).deleteByUserIdAndDictionaryId(USER_ID, DICTIONARY_ID);
    }
}
