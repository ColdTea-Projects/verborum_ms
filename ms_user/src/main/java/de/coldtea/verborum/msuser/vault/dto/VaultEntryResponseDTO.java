package de.coldtea.verborum.msuser.vault.dto;

import lombok.*;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VaultEntryResponseDTO {

    private String vaultEntryId;

    private String userId;

    private String dictionaryId;

    private OffsetDateTime importedAt;

}
