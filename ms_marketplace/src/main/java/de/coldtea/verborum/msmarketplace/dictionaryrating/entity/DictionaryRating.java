package de.coldtea.verborum.msmarketplace.dictionaryrating.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * One user's 1–5 star rating of one listing (P4-19). Only importers rate, and a user has at most one
 * rating per listing (UNIQUE), which they may change or remove. The listing row carries the aggregates
 * that browse reads, so this table is only touched by rating writes and the user.deleted cleanup.
 */
@Getter
@Setter
@ToString
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "dictionary_ratings")
public class DictionaryRating {

    // Server-generated: a system-owned record, like DictionaryImport
    @Id
    @Column(name = "rating_id", updatable = false, nullable = false)
    private String ratingId;

    // Real FK to dictionary_stats with ON DELETE CASCADE — kept while the listing is hidden, gone with it
    @Column(name = "fk_dictionary_id", nullable = false)
    private String dictionaryId;

    // The rater's JWT subject (ms_user's keycloak_id)
    @Column(name = "fk_user_id", nullable = false)
    private String userId;

    // 1–5; validated on the request and by a CHECK constraint
    @Column(name = "stars", nullable = false)
    private Short stars;

    @CreationTimestamp
    @Column(name = "creation_dt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "update_dt")
    private OffsetDateTime updatedAt;
}
