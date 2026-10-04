package de.coldtea.verborum.msdictionary.marketplacemember.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Whether a user is a marketplace member, as last heard from ms_user (P4-16). A copy fed by
 * `user.profile.updated`, never a call to ms_user (rule 5). It decides two things here: the join/leave
 * transition that shares or unshares all of the user's dictionaries, and the rule that a member who has
 * dictionaries keeps at least one shared.
 */
@Getter
@Setter
@ToString
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "marketplace_members")
public class MarketplaceMember {

    // The JWT subject (ms_user's keycloak_id) — the same value dictionaries hold in fk_user_id. As the
    // primary key it makes the consumer an upsert (rule 3)
    @Id
    @Column(name = "keycloak_id", updatable = false, nullable = false)
    private String keycloakId;

    @Column(name = "is_member", nullable = false)
    private Boolean isMember;

    // ms_user's profile updatedAt for the held state — the ordering key (rule 4). Not updatedAt below
    @Column(name = "source_updated_at", nullable = false)
    private OffsetDateTime sourceUpdatedAt;

    @CreationTimestamp
    @Column(name = "creation_dt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "update_dt")
    private OffsetDateTime updatedAt;
}
