package de.coldtea.verborum.msuser.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Published on `user.profile.updated` when a user's display name is set, changed or cleared (P4-13),
 * or their marketplace agreement is accepted or withdrawn (P4-14).
 * Consumed by ms_marketplace, which shows the name on listings, lets users search by it, and hides the
 * listings of a publisher without one.
 * <p>
 * <b>`keycloakId`, not `userId`</b> — the other services key users by the JWT subject, which is this
 * service's `keycloak_id` (same rule as {@link UserDeletedEvent}). Carries the full current name
 * (rule 2): `null` means the user has no display name now.
 * <p>
 * `updatedAt` is the profile's own `updatedAt`, the ordering key (rule 4): a consumer drops an event
 * that is not newer than what it holds, so two quick renames delivered in reverse cannot leave the
 * older name in place. `eventTimestamp` is when the event was raised.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdatedEvent {

    private String keycloakId;

    private String displayName;

    /**
     * Whether the user is on the marketplace (P4-14). ms_marketplace shows their listings only while
     * this is true and `displayName` is set; withdrawing (false) hides them, nothing is deleted.
     */
    private Boolean marketplaceAgreementAccepted;

    private OffsetDateTime updatedAt;

    private OffsetDateTime eventTimestamp;
}
