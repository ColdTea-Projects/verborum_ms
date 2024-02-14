package de.coldtea.verborum.msdictionary.word.repository.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import de.coldtea.verborum.msdictionary.dictionary.entity.Dictionary;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;

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
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID")
    @Column(name = "word_id", updatable = false, nullable = false)
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

    @CreationTimestamp
    @Column(name = "creation_dt", nullable = false, updatable = false)
    private LocalDateTime creationTimestamp;

    @UpdateTimestamp
    @Column(name = "update_dt")
    private LocalDateTime updateTimestamp;

}
