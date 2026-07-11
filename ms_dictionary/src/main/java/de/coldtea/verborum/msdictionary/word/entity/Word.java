package de.coldtea.verborum.msdictionary.word.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import de.coldtea.verborum.msdictionary.dictionary.entity.Dictionary;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "words")
public class Word {
    @Id
    @Column(name = "word_id", updatable = false, nullable = false)
    private String wordId;

    @Column(name = "fk_dictionary_id")
    private String dictionaryId;

    @Column(name = "word")
    private String word;

    // JSON contract for word_meta / translation_meta: a JSON object with optional keys
    // "partOfSpeech" (string), "example" (string), "notes" (string); additional keys allowed.
    // The value must be valid JSON — the DB column type is json (see 2026/07/12-01-changelog.json).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "word_meta", columnDefinition = "json")
    private String wordMeta;

    @Column(name = "translation")
    private String translation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "translation_meta", columnDefinition = "json")
    private String translationMeta;

//    @ManyToOne
//    @JsonBackReference
//    private Dictionary dictionary;

    @CreationTimestamp
    @Column(name = "creation_dt", nullable = false, updatable = false)
    private LocalDateTime creationTimestamp;

    @UpdateTimestamp
    @Column(name = "update_dt")
    private LocalDateTime updateTimestamp;

}
