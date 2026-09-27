package de.coldtea.verborum.msdictionary.word.dto;

import de.coldtea.verborum.msdictionary.common.utils.ValidUUID;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import static de.coldtea.verborum.msdictionary.common.constants.DTOMessageConstants.*;
@Data
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WordRequestDTO {

    @NotBlank(message = WORD_WORD_ID)
    @ValidUUID
    private String wordId;

    @NotBlank(message = WORD_WORD)
    @Size(max = WORD_TEXT_MAX, message = WORD_WORD_TOO_LONG)
    private String word;

    @NotBlank(message = WORD_WORD_META)
    @Size(max = WORD_META_MAX, message = WORD_WORD_META_TOO_LONG)
    private String wordMeta;

    @NotBlank(message = WORD_WORD_TRANSLATION)
    @Size(max = WORD_TEXT_MAX, message = WORD_WORD_TRANSLATION_TOO_LONG)
    private String translation;

    @NotBlank(message = WORD_WORD_TRANSLATION_META)
    @Size(max = WORD_META_MAX, message = WORD_WORD_TRANSLATION_META_TOO_LONG)
    private String translationMeta;

    // Per-user mastery level. Optional so clients that predate the field keep working (additive
    // change); when absent it is stored as null. Client-owned value, but not an arbitrary one:
    // the clients treat anything outside 0..7 as corrupt and rewrite it, so a value they would
    // refuse to read must not be storable in the first place.
    @Min(value = WORD_LEVEL_MIN, message = WORD_LEVEL_OUT_OF_RANGE)
    @Max(value = WORD_LEVEL_MAX, message = WORD_LEVEL_OUT_OF_RANGE)
    private Integer level;

}
