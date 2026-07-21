package de.coldtea.verborum.msuser.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Getter
@Setter
@ToString
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {
    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private String userId;

    // 1:1 link to the Keycloak subject (JWT `sub`). This — not user_id — is the value the
    // other services store as fk_user_id (see docs/integration/frontend-backend-integration.md
    // §3.1), so it is effectively the cross-service join key and must be unique.
    @Column(name = "keycloak_id", nullable = false, unique = true)
    private String keycloakId;

    // NOT NULL + UNIQUE: product rule is one profile per email. Keycloak remains the identity
    // authority; this column is a projection of the Keycloak email kept unique on our side too.
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "display_name")
    private String displayName;

    // Zone-aware timestamps (timestamptz), serialized as ISO-8601 UTC under JSON keys
    // createdAt / updatedAt — same contract as ms_dictionary. Server-authoritative.
    @CreationTimestamp
    @Column(name = "creation_dt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "update_dt")
    private OffsetDateTime updatedAt;
}
