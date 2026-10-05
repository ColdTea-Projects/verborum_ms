package de.coldtea.verborum.msmarketplace.dictionaryrating.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import static de.coldtea.verborum.msmarketplace.common.constants.DTOMessageConstants.*;

/** `PUT /marketplace/dictionaries/{id}/rating` (P4-20) — the caller's stars for one listing. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RatingRequestDTO {

    @NotNull(message = RATING_STARS_REQUIRED)
    @Min(value = RATING_STARS_MIN, message = RATING_STARS_OUT_OF_RANGE)
    @Max(value = RATING_STARS_MAX, message = RATING_STARS_OUT_OF_RANGE)
    private Integer stars;
}
