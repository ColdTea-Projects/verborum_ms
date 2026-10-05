package de.coldtea.verborum.msdictionary.dictionary.dto;

import de.coldtea.verborum.msdictionary.common.utils.SupportedLanguage;
import de.coldtea.verborum.msdictionary.common.utils.ValidUUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import static de.coldtea.verborum.msdictionary.common.constants.DTOMessageConstants.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DictionaryRequestDTO {

    @NotBlank(message = DICTIONARY_DICTIONARY_ID)
    @ValidUUID
    private String dictionaryId;

    @NotBlank(message = DICTIONARY_USER_ID)
    @ValidUUID
    private String userId;

    @NotBlank(message = DICTIONARY_NAME)
    @Size(max = DICTIONARY_NAME_MAX, message = DICTIONARY_NAME_TOO_LONG)
    private String name;

    // P4-18: optional on an update — absent keeps the stored value, so an offline client re-uploading a stale
    // copy cannot undo a Forum join or leave the server applied meanwhile. Required when the dictionary is new;
    // DictionaryServiceImpl checks that, since POST and PUT share one save and either may create
    private Boolean isPublic;

    @NotBlank(message = DICTIONARY_FROM_LANG)
    @SupportedLanguage
    private String fromLang;

    @NotBlank(message = DICTIONARY_TO_LANG)
    @SupportedLanguage
    private String toLang;

}
