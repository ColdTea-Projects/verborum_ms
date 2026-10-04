package de.coldtea.verborum.msdictionary.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Consumed from `user.profile.updated` (P4-16) — ms_user publishes it when a user's display name or
 * marketplace agreement changes. This service only uses the agreement: joining the marketplace shares
 * all the user's dictionaries, leaving makes them all private. This service's copy; only the JSON
 * field names have to agree.
 * <p>
 * <b>`keycloakId`</b> is the JWT subject — this service's `fk_user_id`.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdatedEvent {

    private String keycloakId;

    private String displayName;

    /** Null = an event from before P4-14, which knows nothing about membership — ignored here. */
    private Boolean marketplaceAgreementAccepted;

    /** ms_user's profile updatedAt — the ordering key (rule 4). */
    private OffsetDateTime updatedAt;

    private OffsetDateTime eventTimestamp;
}
