package de.coldtea.verborum.msmarketplace.dictionarystats.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * One marketplace listing per public dictionary — a read model, not a source of truth (decided at
 * P4-03, 2026-07-23). `name`/`fromLang`/`toLang` are a copy of ms_dictionary's values, kept current
 * by events and by the nightly `dictionary.snapshot` reconciliation; browse, filtering and sorting
 * are served from this table alone and never call ms_dictionary.
 * <p>
 * A row is not necessarily a visible listing: a dictionary made private keeps a hidden row
 * (`isListed = false`) — see the field.
 * <p>
 * `rating` and `viewCount` from the original P4-02 field list are deliberately absent: nothing
 * records a view or accepts a rating yet, and the rating scale and one-per-user rule are undecided.
 * They get their own migration once designed.
 */
@Getter
@Setter
@ToString
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "dictionary_stats")
public class DictionaryStats {

    // Neither client- nor server-minted here: it is ms_dictionary's own id, copied from the event.
    // Using it as the primary key is what makes the listeners' upsert idempotent (rule 3) — a
    // redelivered event finds the existing row instead of creating a second listing.
    // No database FK: the dictionaries table lives in another service's database.
    @Id
    @Column(name = "dictionary_id", updatable = false, nullable = false)
    private String dictionaryId;

    // The owner's JWT subject — ms_user's keycloak_id, not its user_id
    @Column(name = "fk_user_id", nullable = false)
    private String userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "from_lang", nullable = false)
    private String fromLang;

    @Column(name = "to_lang", nullable = false)
    private String toLang;

    // fromLang and toLang without direction — `DE-TR` for DE→TR and TR→DE alike (P4-11). Derived, never
    // set on its own: every write that sets the two languages sets this from them, through
    // LanguagePairUtils. Browse filters on it alone, so one indexed IN covers both directions
    @Column(name = "lang_pair", nullable = false)
    private String langPair;

    // The dictionary's tags (P4-12), normalised and sorted — a copy, replaced whole by every newer event.
    // A Postgres varchar[] so the "any of these tags" filter is one indexed `tags && ARRAY[...]`.
    // varchar, not text: Hibernate binds the String[] filter value as varchar[], and Postgres has no
    // text[] && varchar[] operator (found live, P4-12). Unbounded like ms_dictionary's TEXT tag.
    // String[] rather than List: Hibernate's arrayOverlaps predicate takes an array-typed attribute.
    // Set explicitly on create (empty, not null) for the same Hibernate reason as importCount
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", nullable = false, columnDefinition = "varchar[]")
    private String[] tags;

    // false = the dictionary went private: hidden from browse, but the row stays so its
    // sourceUpdatedAt can reject older "public" state arriving late (P4-04). Browse filters on it.
    // Set explicitly on create, for the same Hibernate reason as importCount below
    @Column(name = "is_listed", nullable = false)
    private Boolean isListed;

    // Set explicitly to 0 when a listing is created. The column's DEFAULT 0 does not help: Hibernate
    // inserts every mapped column, so an unset field is written as NULL and violates NOT NULL
    @Column(name = "import_count", nullable = false)
    private Integer importCount;

    // Rating aggregates (P4-19), kept current in the same transaction as every rating write by one atomic
    // UPDATE (DictionaryStatsRepository.applyRatingChange). Set explicitly on create, like importCount.
    // The average shown on a listing is ratingSum / ratingCount; ratingScore is the Bayesian average
    // top-rated browse orders by, so one 5-star vote cannot top the chart
    @Column(name = "rating_count", nullable = false)
    private Integer ratingCount;

    @Column(name = "rating_sum", nullable = false)
    private Integer ratingSum;

    @Column(name = "rating_score", nullable = false)
    private Double ratingScore;

    // When the dictionary (most recently) went public, taken from the event, not from this row's
    // insert time. Kept across updates; reset only when a hidden row is listed again, so a dictionary
    // re-published after a private spell counts as newly published
    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    // ms_dictionary's `updatedAt` for the values held here — the ordering key of rule 4. A consumer
    // ignores any event or snapshot entry whose updatedAt is not newer than this, so out-of-order
    // delivery cannot roll a listing back to older values. Not the same as `updatedAt` below, which
    // is when this row was last written.
    @Column(name = "source_updated_at", nullable = false)
    private OffsetDateTime sourceUpdatedAt;

    @CreationTimestamp
    @Column(name = "creation_dt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "update_dt")
    private OffsetDateTime updatedAt;
}
