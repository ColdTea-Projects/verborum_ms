package de.coldtea.verborum.msuser.vault.dto;

import de.coldtea.verborum.msuser.common.utils.ValidUUID;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import static de.coldtea.verborum.msuser.common.constants.DTOMessageConstants.*;

/**
 * Body of POST /users/{userId}/vault. Carries only the imported dictionary — the vaultEntryId is
 * server-generated (a vault entry is a system-owned row, not a client-authored object: P2-09
 * creates the same rows from a marketplace event where no client id exists), and the userId comes
 * from the path.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VaultEntryRequestDTO {

    @NotBlank(message = VAULT_ENTRY_DICTIONARY_ID)
    @ValidUUID(fieldName = DICTIONARY_ID)
    private String dictionaryId;

}
