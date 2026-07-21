package de.coldtea.verborum.msdictionary.dictionary.entity;

import de.coldtea.verborum.msdictionary.word.entity.Word;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@ToString
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "dictionaries")
public class Dictionary {
    @Id
    @Column(name = "dictionary_id", updatable = false, nullable = false)
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

//    @OneToMany(mappedBy = "dictionary")
//    @ToString.Exclude
//    private List<Word> words;

    // Zone-aware timestamps. Stored as timestamptz, serialized to ISO-8601 WITH offset. JSON keys
    // are createdAt / updatedAt to match the clients. Server-authoritative — set by Hibernate.
    @CreationTimestamp
    @Column(name = "creation_dt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "update_dt")
    private OffsetDateTime updatedAt;
}
