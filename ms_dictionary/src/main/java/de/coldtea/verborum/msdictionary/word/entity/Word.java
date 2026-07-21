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

import java.time.OffsetDateTime;

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

    // Canonical client contract (see docs/integration/frontend-backend-integration.md §4.2 and
    // the Android doc §4). Stored opaquely by the backend. word / translation each hold a JSON
    // ARRAY of per-meaning surface forms as a string — one entry per meaning, article included
    // where the language composes one: e.g. ["der Apfel"], ["kaufen","erwerben"], ["l'eau"].
    // Column type is TEXT (see 2026/07/21-01-changelog.json) so multi-meaning entries are not
    // truncated; JSON validity is not enforced at the DB.
    @Column(name = "word")
    private String word;

    // Canonical client contract for word_meta / translation_meta: one JSON OBJECT
    //   { "lang": "de",          // lowercase two-letter code of this side's language
    //     "type": "verb",        // part of speech; absent for free text
    //     "genders": ["m",""],   // codes m/f/n/c, index-aligned to the surfaces array; omitted if none
    //     "fields": {            // grammatical form key -> list of values, index-aligned per meaning
    //       "past": ["kaufte","erwarb"], "aux": ["haben","haben"] } }
    // All lists are index-aligned to the surfaces array; keys empty in every meaning are omitted;
    // unknown keys must be ignored (schema-evolution rule). Field keys in use: reading, plural,
    // feminine, comparative, superlative, present, past, past3, participle, aux, aspect, root,
    // stem, measure, class, polite. The value must be valid JSON — column type is json
    // (see 2026/07/12-01-changelog.json).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "word_meta", columnDefinition = "json")
    private String wordMeta;

    @Column(name = "translation")
    private String translation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "translation_meta", columnDefinition = "json")
    private String translationMeta;

    // Per-user mastery/proficiency for this word (how well the user knows it). Client-owned:
    // the mobile practice engine sets it, the backend stores it opaquely. Nullable on purpose —
    // clients that predate this field simply omit it (null = "not provided"; treat as 0 client-side).
    @Column(name = "level")
    private Integer level;

//    @ManyToOne
//    @JsonBackReference
//    private Dictionary dictionary;

    // Zone-aware timestamps. Stored as timestamptz, serialized to ISO-8601 WITH offset
    // (e.g. "2026-07-21T12:34:56.789+04:00") so every client can resolve the absolute instant.
    // JSON keys are createdAt / updatedAt to match the clients. Server-authoritative — set by
    // Hibernate, ignored on write.
    @CreationTimestamp
    @Column(name = "creation_dt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "update_dt")
    private OffsetDateTime updatedAt;

}
