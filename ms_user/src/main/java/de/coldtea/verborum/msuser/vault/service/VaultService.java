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
    /**
     * Adds a marketplace import to the vault. A first add publishes `vault.changed` (P4N-14) so the
     * user's other devices sync; a redelivery changes nothing and says nothing. `deviceId` is the
     * importing install, which that nudge skips — null when the client sent none.
     */
    VaultEntryResponseDTO importDictionary(String keycloakId, String dictionaryId, String deviceId);

    default VaultEntryResponseDTO importDictionary(String keycloakId, String dictionaryId) {
        return importDictionary(keycloakId, dictionaryId, null);
    }

    void deleteVaultEntry(String userId, String dictionaryId, String callerKeycloakId);
}
