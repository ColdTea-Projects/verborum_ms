package de.coldtea.verborum.msuser.vault.controller;

import de.coldtea.verborum.msuser.common.response.Response;
import de.coldtea.verborum.msuser.vault.dto.VaultEntryRequestDTO;
import de.coldtea.verborum.msuser.vault.dto.VaultEntryResponseDTO;
import de.coldtea.verborum.msuser.vault.service.VaultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

import static de.coldtea.verborum.msuser.common.constants.ResponseMessageConstants.*;
import static de.coldtea.verborum.msuser.common.utils.ResponseUtils.buildResponse;

@RestController
@RequestMapping("/users/{userId}/vault")
@RequiredArgsConstructor
public class VaultController {

    private final VaultService vaultService;

    @GetMapping
    public ResponseEntity<List<VaultEntryResponseDTO>> getVaultEntriesByUser(@PathVariable String userId) {
        return new ResponseEntity<>(vaultService.getVaultEntriesByUser(userId), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<Response> addVaultEntry(@PathVariable String userId,
                                                  @Valid @RequestBody VaultEntryRequestDTO vaultEntry,
                                                  WebRequest request) {
        VaultEntryResponseDTO vaultEntryResponseDTO = vaultService.addVaultEntry(userId, vaultEntry);
        return buildResponse(HttpStatus.CREATED, VAULT_ENTRY_SAVED_SUCCESSFULLY,
                vaultEntryResponseDTO.getVaultEntryId(), request);
    }

    @DeleteMapping("/{dictionaryId}")
    public ResponseEntity<Response> deleteVaultEntry(@PathVariable String userId,
                                                     @PathVariable String dictionaryId,
                                                     WebRequest request) {
        vaultService.deleteVaultEntry(userId, dictionaryId);
        return buildResponse(HttpStatus.OK, VAULT_ENTRY_DELETED_SUCCESSFULLY, dictionaryId, request);
    }

}
