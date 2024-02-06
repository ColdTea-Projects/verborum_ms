package de.coldtea.verborum.msdictionary.word.service;

import jakarta.validation.constraints.NotBlank;

public class WordDTO {

    @NotBlank(message = "wordId is mandatory")
    private String wordId;

    @NotBlank(message = "word is mandatory")
    private String word;

    @NotBlank(message = "wordMeta is mandatory")
    private String wordMeta;

    @NotBlank(message = "translation is mandatory")
    private String translation;

    @NotBlank(message = "translationMeta is mandatory")
    private String translationMeta;

    public WordDTO() {
    }

    public String getWordId() {
        return wordId;
    }

    public void setWordId(String wordId) {
        this.wordId = wordId;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getWordMeta() {
        return wordMeta;
    }

    public void setWordMeta(String wordMeta) {
        this.wordMeta = wordMeta;
    }

    public String getTranslation() {
        return translation;
    }

    public void setTranslation(String translation) {
        this.translation = translation;
    }

    public String getTranslationMeta() {
        return translationMeta;
    }

    public void setTranslationMeta(String translationMeta) {
        this.translationMeta = translationMeta;
    }

    @Override
    public String toString() {
        return "WordDTO{" +
                "wordId='" + wordId + '\'' +
                ", word='" + word + '\'' +
                ", wordMeta='" + wordMeta + '\'' +
                ", translation='" + translation + '\'' +
                ", translationMeta='" + translationMeta + '\'' +
                '}';
    }
}
