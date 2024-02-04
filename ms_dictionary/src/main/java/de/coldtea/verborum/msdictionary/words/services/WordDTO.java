package de.coldtea.verborum.msdictionary.words.services;

import com.fasterxml.jackson.annotation.JsonBackReference;
import de.coldtea.verborum.msdictionary.dictionaries.repository.Dictionary;
import jakarta.persistence.*;
import lombok.Getter;

public class WordDTO {

    private String wordId;
    private String word;
    private String wordMeta;
    private String translation;
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
