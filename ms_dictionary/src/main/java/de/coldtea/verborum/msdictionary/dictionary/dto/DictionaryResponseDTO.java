package de.coldtea.verborum.msdictionary.dictionary.dto;

import static de.coldtea.verborum.msdictionary.common.constants.DTOMessageConstants.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DictionaryResponseDTO {

    @NotBlank(message = DICTIONARY_DICTIONARY_ID)
    private String dictionaryId;

    @NotBlank(message = DICTIONARY_USER_ID)
    private String userId;

    @NotBlank(message = DICTIONARY_NAME)
    private String name;

    @NotBlank(message = DICTIONARY_IS_PUBLIC)
    private Boolean isPublic;

    @NotBlank(message = DICTIONARY_FROM_LANG)
    private String fromLang;

    @NotBlank(message = DICTIONARY_TO_LANG)
    private String toLang;

}
