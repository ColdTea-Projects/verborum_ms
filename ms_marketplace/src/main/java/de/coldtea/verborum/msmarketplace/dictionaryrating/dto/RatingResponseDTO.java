package de.coldtea.verborum.msmarketplace.dictionaryrating.dto;

import lombok.*;

import java.time.OffsetDateTime;

/** The caller's own rating of one listing (P4-20), for showing their stars on the details screen. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RatingResponseDTO {

    private String dictionaryId;

    private Integer stars;

    // When the rating was last set or changed
    private OffsetDateTime ratedAt;
}
