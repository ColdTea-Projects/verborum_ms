package de.coldtea.verborum.msdictionary.dictionary.service.dto;

import static de.coldtea.verborum.msdictionary.common.constants.DTOMessageConstants.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
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

    public DictionaryResponseDTO(String dictionaryId, String userId, String name, Boolean isPublic, String fromLang, String toLang) {
        this.dictionaryId = dictionaryId;
        this.userId = userId;
        this.name = name;
        this.isPublic = isPublic;
        this.fromLang = fromLang;
        this.toLang = toLang;
    }

    public void setDictionaryId(String dictionaryId) {
        this.dictionaryId = dictionaryId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPublic(Boolean aPublic) {
        isPublic = aPublic;
    }

    public void setFromLang(String fromLang) {
        this.fromLang = fromLang;
    }

    public void setToLang(String toLang) {
        this.toLang = toLang;
    }

    @Override
    public String toString() {
        return "DictionaryResponseDTO{" +
                "dictionaryId='" + dictionaryId + '\'' +
                ", userId='" + userId + '\'' +
                ", name='" + name + '\'' +
                ", isPublic=" + isPublic +
                ", fromLang='" + fromLang + '\'' +
                ", toLang='" + toLang + '\'' +
                '}';
    }
}
