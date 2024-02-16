package de.coldtea.verborum.msdictionary.dictionary.dto;

import de.coldtea.verborum.msdictionary.common.utils.SupportedLanguage;
import de.coldtea.verborum.msdictionary.common.utils.ValidUUID;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import static de.coldtea.verborum.msdictionary.common.constants.DTOMessageConstants.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DictionaryRequestDTO {

    @NotBlank(message = DICTIONARY_DICTIONARY_ID)
    @ValidUUID(fieldName = DICTIONARY_ID)
    private String dictionaryId;

    @NotBlank(message = DICTIONARY_USER_ID)
    @ValidUUID(fieldName = USER_ID)
    private String userId;

    @NotBlank(message = DICTIONARY_NAME)
    private String name;

    @NotBlank(message = DICTIONARY_IS_PUBLIC)
    private Boolean isPublic;

    @NotBlank(message = DICTIONARY_FROM_LANG)
    @SupportedLanguage
    private String fromLang;

    @NotBlank(message = DICTIONARY_TO_LANG)
    @SupportedLanguage
    private String toLang;

}
