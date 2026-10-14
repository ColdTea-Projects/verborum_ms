package de.coldtea.verborum.msnotification.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Consumed from `user.deleted` (ms_user). Carries both ids; this service stores the JWT subject, which
 * is ms_user's `keycloakId` — so the cascade must match on <b>`keycloakId`</b>. Matching on `userId`
 * deletes nothing and looks like success.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDeletedEvent {

    private String userId;

    private String keycloakId;

    private OffsetDateTime eventTimestamp;
}
