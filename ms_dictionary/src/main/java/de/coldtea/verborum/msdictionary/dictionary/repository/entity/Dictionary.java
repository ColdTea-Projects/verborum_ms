package de.coldtea.verborum.msdictionary.dictionary.repository.entity;

import de.coldtea.verborum.msdictionary.word.repository.entity.Word;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.List;
import java.util.Objects;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    @ToString.Exclude
    private List<Word> words;

}
