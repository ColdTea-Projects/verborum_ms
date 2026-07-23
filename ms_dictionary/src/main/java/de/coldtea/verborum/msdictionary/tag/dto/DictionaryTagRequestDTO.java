package de.coldtea.verborum.msdictionary.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import static de.coldtea.verborum.msdictionary.common.constants.DTOMessageConstants.TAG_TAG;
import static de.coldtea.verborum.msdictionary.common.constants.DTOMessageConstants.TAG_TAG_TOO_LONG;

/**
 * Body of POST /dictionaries/{dictionaryId}/tags. Carries only the tag text — the dictionary comes
 * from the path and the id is server-generated.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DictionaryTagRequestDTO {

    @NotBlank(message = TAG_TAG)
    @Size(max = 50, message = TAG_TAG_TOO_LONG)
    private String tag;

}
