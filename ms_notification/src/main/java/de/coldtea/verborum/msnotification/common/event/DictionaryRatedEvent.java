package de.coldtea.verborum.msnotification.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Consumed from `dictionary.rated` (ms_marketplace, P4N-06): a rating was created or its stars changed.
 * `raterKeycloakId` is the actor, `publisherId` the recipient; both are JWT subjects. The stars are not
 * used — a push carries ids only and the app fetches the numbers itself.
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
