package de.coldtea.verborum.msdictionary.word.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import static de.coldtea.verborum.msdictionary.common.constants.DTOMessageConstants.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WordResponseDTO {

    @NotBlank(message = WORD_WORD_ID)
    private String wordId;

    @NotBlank(message = WORD_DICTIONARY_ID)
    private String dictionaryId;

    @NotBlank(message = WORD_WORD)
    private String word;

    @NotBlank(message = WORD_WORD_META)
    private String wordMeta;

    @NotBlank(message = WORD_WORD_TRANSLATION)
    private String translation;

    @NotBlank(message = WORD_WORD_TRANSLATION_META)
    private String translationMeta;

}
