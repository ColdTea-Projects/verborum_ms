package de.coldtea.verborum.msdictionary.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Consumed from `user.deleted`, published by ms_user when a profile is removed. ms_dictionary
 * reacts by deleting that user's dictionaries and their words.
 * <p>
 * Consumer-side copy of ms_user's event (per docs/agent/rabbitmq.md, event DTOs live in the
 * publishing service and are duplicated on the consuming side). The class names deliberately do not
 * have to match across services — see the INFERRED type mapper in RabbitMQConfig — but the JSON
 * field names do.
 * <p>
 * <b>Match on `keycloakId`, never on `userId`.</b> This service's `fk_user_id` holds the JWT
 * subject, which is ms_user's `keycloak_id`. `userId` is ms_user's own primary key and matches
 * nothing here — using it would delete zero rows and report success.
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
