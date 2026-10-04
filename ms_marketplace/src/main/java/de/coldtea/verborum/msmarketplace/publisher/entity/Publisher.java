package de.coldtea.verborum.msmarketplace.publisher.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * A user's public display name, as the marketplace knows it (P4-13) — a copy of ms_user's
 * `display_name` and marketplace agreement, kept current by `user.profile.updated`. Listings show the
 * name, the name filter searches it, and browse hides every listing whose publisher has no row here, a
 * null name, or has not accepted the marketplace terms (P4-14) — the marketplace is opt-in.
 * <p>
 * Joined to listings by `keycloakId` = `dictionary_stats.fk_user_id`, in queries only — no JPA
 * association and no FK, because a listing can arrive before its publisher's name does.
 */
@Getter
@Setter
@ToString
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "publishers")
public class Publisher {

    // The JWT subject (ms_user's keycloak_id) — the same value listings hold in fk_user_id. As the
    // primary key it makes the consumer an upsert (rule 3)
    @Id
    @Column(name = "keycloak_id", updatable = false, nullable = false)
    private String keycloakId;

    // Trimmed; blank is stored as null. Null = no name now: the row is kept so its sourceUpdatedAt
    // still rejects an older "named" event arriving late (rule 4)
    @Column(name = "display_name")
    private String displayName;

    // The user accepted the marketplace terms (P4-14). Together with a display name it decides whether
    // their listings are shown at all; false = never accepted or withdrawn. Set explicitly on create —
    // Hibernate writes every mapped column, so the column default would not apply
    @Column(name = "marketplace_agreement_accepted", nullable = false)
    private Boolean marketplaceAgreementAccepted;

    // ms_user's updatedAt for the held name — the ordering key. Not the same as updatedAt below
    @Column(name = "source_updated_at", nullable = false)
    private OffsetDateTime sourceUpdatedAt;

    @CreationTimestamp
    @Column(name = "creation_dt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "update_dt")
    private OffsetDateTime updatedAt;
}
