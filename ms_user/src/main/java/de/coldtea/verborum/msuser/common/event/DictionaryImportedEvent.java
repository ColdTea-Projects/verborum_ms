package de.coldtea.verborum.msuser.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Consumed from `dictionary.imported` when a user imports a public dictionary from the marketplace.
 * ms_user reacts by adding a VaultEntry.
 * <p>
 * <b>This is the consumer-side copy of an event ms_marketplace does not publish yet</b>
 * (roadmap P4-07). Per docs/agent/rabbitmq.md an event DTO belongs to the publishing service and is
 * duplicated on the consuming side; this class defines the contract P4-07 must publish. The
 * listener does not depend on the sender's class name — see the type mapper note in RabbitMQConfig.
 * <p>
 * <b>`keycloakId` is the user identity on this event, not ms_user's `userId`.</b> ms_marketplace
 * only ever sees the JWT subject, which is this service's `keycloak_id`; ms_user's own `user_id` is
 * private to ms_user and unknown to the publisher. VaultEntry.fk_user_id is a real FK to
 * users(user_id), so the listener resolves keycloakId → user_id before writing (see
 * VaultServiceImpl.importDictionary). Same rule as `user.deleted` — see verborum.md.
 * <p>
 * `eventTimestamp` is zone-aware `OffsetDateTime`, serialized as ISO-8601 with an offset, matching
 * every other event on this exchange (changed from `LocalDateTime` in the 2026-07-23 review).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryImportedEvent {

    private String dictionaryId;

    private String keycloakId;

    private OffsetDateTime eventTimestamp;
}
