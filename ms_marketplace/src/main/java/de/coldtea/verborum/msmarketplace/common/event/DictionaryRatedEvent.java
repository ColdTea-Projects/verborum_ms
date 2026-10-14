package de.coldtea.verborum.msmarketplace.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Published on `dictionary.rated` (P4N-06) when a rating is created or its stars change. Consumed by
 * ms_notification, which sends the publisher a silent `REFRESH` push so their app updates its numbers.
 * <p>
 * Never published for a repeat of the same stars, nor for a removal. `publisherId` and `raterKeycloakId`
 * are JWT subjects (ms_user's `keycloak_id`); the recipient is the publisher and the actor is the rater,
 * so the consumer can also refuse to notify someone about their own action. No dictionary name travels
 * on it: the app fetches the data itself.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryRatedEvent {

    private String dictionaryId;

    private String publisherId;

    private String raterKeycloakId;

    private Integer stars;

    private OffsetDateTime eventTimestamp;
}
