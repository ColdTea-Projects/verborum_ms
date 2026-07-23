package de.coldtea.verborum.msdictionary.tag.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * One tag on one dictionary — a dictionary carries many rows here.
 * <p>
 * Intended consumers: marketplace discovery (browse by topic) and the later AI work that predicts
 * which words a user is likely to meet. Tags are therefore stored normalised (see
 * DictionaryTagServiceImpl) so "Food", "food " and "FOOD" aggregate as one thing.
 * <p>
 * <b>Real DB FK with ON DELETE CASCADE</b>, unlike the `word → dictionary` reference. That one has no
 * FK because words are split-ready — they could move to their own service. A tag is a same-service
 * satellite of Dictionary with no independent life, so the FK is the correct model here and gives
 * free, correct cleanup when a dictionary is deleted. Same reasoning as UserStats/VaultEntry in
 * ms_user.
 * <p>
 * Not mapped as a JPA association (project convention: joins are explicit repository calls).
 */
@Getter
@Setter
@ToString
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "dictionary_tags")
public class DictionaryTag {

    // Server-generated, not client-supplied like dictionaryId/wordId: a tag is a system-owned row,
    // and the clients do not track tag identity — they send a string. Same call as VaultEntry.
    @Id
    @Column(name = "tag_id", updatable = false, nullable = false)
    private String tagId;

    @Column(name = "fk_dictionary_id", nullable = false, updatable = false)
    private String dictionaryId;

    // UNIQUE (fk_dictionary_id, tag) in the migration keeps a dictionary's tags a set
    @Column(name = "tag", nullable = false, length = 50)
    private String tag;

    @CreationTimestamp
    @Column(name = "creation_dt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
