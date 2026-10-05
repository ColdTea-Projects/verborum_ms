package de.coldtea.verborum.msuser.vault.service;

import de.coldtea.verborum.msuser.vault.dto.VaultEntryResponseDTO;

import java.util.List;

public interface VaultService {
    /**
     * The `callerKeycloakId` arguments are the JWT subject; a vault may only be read or changed by
     * the user it belongs to (P3-05). There is no public add (SEC-09). `importDictionary` has no such argument on purpose — it is the
     * event-driven path, where the actor is ms_marketplace, not a logged-in caller.
     */
    List<VaultEntryResponseDTO> getVaultEntriesByUser(String userId, String callerKeycloakId);
    VaultEntryResponseDTO importDictionary(String keycloakId, String dictionaryId);
    void deleteVaultEntry(String userId, String dictionaryId, String callerKeycloakId);
}
