package de.coldtea.verborum.msmarketplace.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Consumed from `user.profile.updated` (P4-13) — ms_user publishes it when a user's display name is
 * set, changed or cleared. This service's copy; only the JSON field names have to agree.
 * <p>
 * <b>`keycloakId`, not `userId`</b> — the other services key users by the JWT subject, which is this
 * ms_user's `keycloak_id` (same rule as {@link UserDeletedEvent}). Carries the full current name
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
     * Whether the user is on the marketplace (P4-14): their listings show only while this is true and
     * they have a display name. Null = an event from before P4-14 — keep what is held.
     */
    private Boolean marketplaceAgreementAccepted;

    private OffsetDateTime updatedAt;

    private OffsetDateTime eventTimestamp;
}
