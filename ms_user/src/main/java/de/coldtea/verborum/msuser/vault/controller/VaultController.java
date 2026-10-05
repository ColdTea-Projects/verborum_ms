package de.coldtea.verborum.msuser.vault.controller;

import de.coldtea.verborum.msuser.common.response.Response;
import de.coldtea.verborum.msuser.vault.dto.VaultEntryResponseDTO;
import de.coldtea.verborum.msuser.vault.service.VaultService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

import static de.coldtea.verborum.msuser.common.constants.ResponseMessageConstants.*;
import static de.coldtea.verborum.msuser.common.utils.ResponseUtils.buildResponse;
import static de.coldtea.verborum.msuser.common.utils.SecurityUtils.getCurrentKeycloakId;

/**
 * Read and prune the caller's vault. There is deliberately no POST (SEC-09): an entry is added only by
 * a marketplace import (`POST /marketplace/dictionaries/{id}/import` → `dictionary.imported` →
 * VaultService.importDictionary), which is where the Forum gate, the "not your own" rule and the
 * import count live. A direct add accepted any dictionary id — another user's private one, or one
 * added by a non-member — and skipped all three.
 */
@RestController
@RequestMapping("/users/{userId}/vault")
@RequiredArgsConstructor
public class VaultController {

    private final VaultService vaultService;

    @GetMapping
    public ResponseEntity<List<VaultEntryResponseDTO>> getVaultEntriesByUser(@PathVariable String userId) {
        return new ResponseEntity<>(vaultService.getVaultEntriesByUser(userId, getCurrentKeycloakId()), HttpStatus.OK);
    }

    @DeleteMapping("/{dictionaryId}")
    public ResponseEntity<Response> deleteVaultEntry(@PathVariable String userId,
                                                     @PathVariable String dictionaryId,
                                                     WebRequest request) {
        vaultService.deleteVaultEntry(userId, dictionaryId, getCurrentKeycloakId());
        return buildResponse(HttpStatus.OK, VAULT_ENTRY_DELETED_SUCCESSFULLY, dictionaryId, request);
    }

}
