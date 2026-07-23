package de.coldtea.verborum.msuser.vault.service;

import de.coldtea.verborum.msuser.vault.dto.VaultEntryRequestDTO;
import de.coldtea.verborum.msuser.vault.dto.VaultEntryResponseDTO;

import java.util.List;

public interface VaultService {
    List<VaultEntryResponseDTO> getVaultEntriesByUser(String userId);
    VaultEntryResponseDTO addVaultEntry(String userId, VaultEntryRequestDTO vaultEntryRequestDTO);
    VaultEntryResponseDTO importDictionary(String keycloakId, String dictionaryId);
    void deleteVaultEntry(String userId, String dictionaryId);
}
