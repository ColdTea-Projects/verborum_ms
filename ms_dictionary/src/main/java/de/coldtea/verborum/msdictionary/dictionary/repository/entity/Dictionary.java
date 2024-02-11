package de.coldtea.verborum.msdictionary.dictionary.repository.entity;

import de.coldtea.verborum.msdictionary.word.repository.entity.Word;
import jakarta.persistence.*;
import lombok.Getter;

import java.util.List;

@Getter
@Entity
@Table(name = "dictionaries")
public class Dictionary {
    @Getter
    @Id
    @Column(name = "dictionary_id")
    private String dictionaryId;
    @Column(name = "fk_user_id")
    private String userId;
    @Column(name = "name")
    private String name;
    @Column(name = "is_public")
    private Boolean isPublic;
    @Getter
    @Column(name = "from_lang")
    private String fromLang;
    @Column(name = "to_lang")
    private String toLang;

    @OneToMany(mappedBy = "dictionary")
    private List<Word> words;

    public Dictionary(String dictionaryId, String userId, String name, Boolean isPublic, String fromLang, String toLang) {
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
        return "Dictionary{" +
                "dictionaryId='" + dictionaryId + '\'' +
                ", userId='" + userId + '\'' +
                ", name='" + name + '\'' +
                ", isPublic=" + isPublic +
                ", fromLang='" + fromLang + '\'' +
                ", toLang='" + toLang + '\'' +
                '}';
    }
}