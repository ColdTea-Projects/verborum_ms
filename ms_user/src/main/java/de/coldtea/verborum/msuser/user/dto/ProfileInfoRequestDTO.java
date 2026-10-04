package de.coldtea.verborum.msuser.user.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

import static de.coldtea.verborum.msuser.common.constants.DTOMessageConstants.*;

/**
 * `PUT /users/me/profile-info` (P4-14) — the fields a user edits on their profile page. Every field is
 * optional: one that is absent (null) is left as it is, never cleared. An empty `displayName` removes
 * the name, which is only allowed while the marketplace agreement is not accepted.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileInfoRequestDTO {

    @Size(max = USER_DISPLAY_NAME_MAX, message = USER_DISPLAY_NAME_TOO_LONG)
    private String displayName;

    // true = accept (needs a display name and a version), false = withdraw from the marketplace
    private Boolean marketplaceAgreementAccepted;

    // Which terms text the user was shown — required when accepting
    @Size(max = MARKETPLACE_AGREEMENT_VERSION_MAX, message = MARKETPLACE_AGREEMENT_VERSION_TOO_LONG)
    private String marketplaceAgreementVersion;
}
