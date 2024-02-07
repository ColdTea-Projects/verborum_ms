package de.coldtea.verborum.msdictionary.word.service;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

import static de.coldtea.verborum.msdictionary.common.constants.DTOMessageConstants.*;

@Getter
public class WordRequestDTO {

    @NotBlank(message = WORD_WORD_ID)
    private String wordId;

    @NotBlank(message = WORD_WORD)
    private String word;

    @NotBlank(message = WORD_WORD_META)
    private String wordMeta;

    @NotBlank(message = WORD_WORD_TRANSLATION)
    private String translation;

    @NotBlank(message = WORD_WORD_TRANSLATION_META)
    private String translationMeta;

    public WordRequestDTO() {
    }

    public void setWordId(String wordId) {
        this.wordId = wordId;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public void setWordMeta(String wordMeta) {
        this.wordMeta = wordMeta;
    }

    public void setTranslation(String translation) {
        this.translation = translation;
    }

    public void setTranslationMeta(String translationMeta) {
        this.translationMeta = translationMeta;
    }

    @Override
    public String toString() {
        return "WordRequestDTO{" +
                "wordId='" + wordId + '\'' +
                ", word='" + word + '\'' +
                ", wordMeta='" + wordMeta + '\'' +
                ", translation='" + translation + '\'' +
                ", translationMeta='" + translationMeta + '\'' +
                '}';
    }
}
