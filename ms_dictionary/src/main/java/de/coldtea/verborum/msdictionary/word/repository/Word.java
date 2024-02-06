package de.coldtea.verborum.msdictionary.word.repository;

import com.fasterxml.jackson.annotation.JsonBackReference;
import de.coldtea.verborum.msdictionary.dictionary.repository.Dictionary;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "words")
public class Word {
    @Id
    @Column(name = "word_id")
    private String wordId;
    @Column(name = "fk_dictionary_id")
    private String dictionaryId;
    @Column(name = "word")
    private String word;
    @Column(name = "word_meta")
    private String wordMeta;
    @Column(name = "translation")
    private String translation;
    @Column(name = "translation_meta")
    private String translationMeta;

    @ManyToOne
    @JsonBackReference
    private Dictionary dictionary;

    public Word() {
    }

    public Word(String wordId, String dictionaryId, String word, String wordMeta, String translation, String translationMeta) {
        this.wordId = wordId;
        this.dictionaryId = dictionaryId;
        this.word = word;
        this.wordMeta = wordMeta;
        this.translation = translation;
        this.translationMeta = translationMeta;
    }

    public String getWordId() {
        return wordId;
    }

    public void setWordId(String wordId) {
        this.wordId = wordId;
    }

    public String getDictionaryId() {
        return dictionaryId;
    }

    public void setDictionaryId(String dictionaryId) {
        this.dictionaryId = dictionaryId;
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
        return "Word{" +
                "wordId='" + wordId + '\'' +
                ", dictionaryId='" + dictionaryId + '\'' +
                ", word='" + word + '\'' +
                ", wordMeta='" + wordMeta + '\'' +
                ", translation='" + translation + '\'' +
                ", translationMeta='" + translationMeta + '\'' +
                '}';
    }
}
