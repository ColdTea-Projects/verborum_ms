package de.coldtea.verborum.msuser.user.dto;

import lombok.*;

/**
 * `GET /users/me` (P4-14) — what the client needs for the profile page right after login. `id` is
 * ms_user's `userId`, the id the other `/users/{userId}` endpoints take.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileResponseDTO {

    private String id;

    private String email;

    private String displayName;

    private Boolean marketplaceAgreementAccepted;

    private String marketplaceAgreementVersion;
}
