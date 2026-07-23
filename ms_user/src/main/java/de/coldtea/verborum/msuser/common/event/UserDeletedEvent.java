package de.coldtea.verborum.msuser.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Published on `user.deleted` when a profile is removed. Consumed by ms_dictionary (P2-10) and
 * ms_marketplace to cascade-delete that user's data.
 * <p>
 * <b>Carries both ids on purpose.</b> `userId` is ms_user's own primary key, but the other services
 * store the JWT subject — which is this user's `keycloakId` — in their `fk_user_id` columns. A
 * consumer cascading in ms_dictionary or ms_marketplace must therefore match on
 * <b>`keycloakId`</b>; matching on `userId` would silently delete nothing. `userId` is carried so a
 * consumer that does hold an ms_user reference (or a human reading the DLQ) can correlate the two.
 * <p>
 * `eventTimestamp` is `LocalDateTime` to match the events ms_dictionary already publishes — the
 * whole event wire format is shared, so this stays aligned rather than adopting the zone-aware
 * `OffsetDateTime` used on the REST DTOs. Serialized as ISO-8601; see RabbitMQConfig.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDeletedEvent {

    private String userId;

    private String keycloakId;

    private LocalDateTime eventTimestamp;
}
