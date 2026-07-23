package de.coldtea.verborum.msdictionary.tag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import static de.coldtea.verborum.msdictionary.common.constants.DTOMessageConstants.TAG_TAG;

/**
 * Body of POST /dictionaries/{dictionaryId}/tags. Carries only the tag text — the dictionary comes
 * from the path and the id is server-generated.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DictionaryTagRequestDTO {

    // No length cap — the column is TEXT. Only "must not be blank" is enforced.
    @NotBlank(message = TAG_TAG)
    private String tag;

}
