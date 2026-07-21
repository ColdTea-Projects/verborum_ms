package de.coldtea.verborum.msuser.vault.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "vault_entries")
public class VaultEntry {
    @Id
    @Column(name = "vault_entry_id", updatable = false, nullable = false)
    private String vaultEntryId;

    // FK to users.user_id (a vault entry belongs to one user). Same intra-service satellite case
    // as UserStats: real DB FK with ON DELETE CASCADE, enforced in the migration, not mapped as a
    // JPA association (project convention).
    @Column(name = "fk_user_id", nullable = false)
    private String userId;

    // Cross-service reference to a dictionary owned by ms_dictionary. Plain String, NO DB-level FK
    // (the genuine cross-service case the "no FK" convention is for — the row lives in another
    // service's database).
    @Column(name = "fk_dictionary_id", nullable = false)
    private String dictionaryId;

    @CreationTimestamp
    @Column(name = "imported_at", nullable = false, updatable = false)
    private LocalDateTime importedAt;
}
