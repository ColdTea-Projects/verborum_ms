package de.coldtea.verborum.msuser.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

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
 * `eventTimestamp` is zone-aware `OffsetDateTime`, serialized as ISO-8601 with an offset. It was
 * `LocalDateTime` until the 2026-07-23 review pointed out that this is the same ambiguity P0-19/P0-20
 * removed from the entity timestamps: once publisher and consumer run in containers with different
 * default zones, a zoneless event timestamp is unusable for ordering or for reading a DLQ.
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
