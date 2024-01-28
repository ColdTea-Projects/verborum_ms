package de.coldtea.verborum.msdictionary.dictionaries.entity;

import de.coldtea.verborum.msdictionary.words.entity.Word;
import jakarta.persistence.*;
import lombok.Getter;

import java.util.List;

@Getter
@Entity
@Table(name = "dictionaries")
public class Dictionary {
    @Id
    @Column(name = "dictionary_id")
    private String dictionaryId;
    @Column(name = "fk_user_id")
    private String userId;
    @Column(name = "name")
    private String name;
    @Column(name = "is_public")
    private Boolean isPublic;
    @Column(name = "from_lang")
    private String fromLang;
    @Column(name = "to_lang")
    private String toLang;

    @OneToMany(mappedBy = "dictionary")
    private List<Word> words;

    public Dictionary() {
    }

    public String getDictionaryId() {
        return dictionaryId;
    }

    public void setDictionaryId(String dictionaryId) {
        this.dictionaryId = dictionaryId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getPublic() {
        return isPublic;
    }

    public void setPublic(Boolean aPublic) {
        isPublic = aPublic;
    }

    public String getFromLang() {
        return fromLang;
    }

    public void setFromLang(String fromLang) {
        this.fromLang = fromLang;
    }

    public String getToLang() {
        return toLang;
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
