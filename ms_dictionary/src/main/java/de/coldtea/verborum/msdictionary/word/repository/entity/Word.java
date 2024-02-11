package de.coldtea.verborum.msdictionary.word.repository.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import de.coldtea.verborum.msdictionary.dictionary.repository.entity.Dictionary;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "words")
public class Word {
    @Getter
    @Id
    @Column(name = "word_id")
    private String wordId;
    @Getter
    @Column(name = "fk_dictionary_id")
    private String dictionaryId;
    @Getter
    @Column(name = "word")
    private String word;
    @Getter
    @Column(name = "word_meta")
    private String wordMeta;
    @Getter
    @Column(name = "translation")
    private String translation;
    @Getter
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

    public void setWordId(String wordId) {
        this.wordId = wordId;
    }

    public void setDictionaryId(String dictionaryId) {
        this.dictionaryId = dictionaryId;
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
