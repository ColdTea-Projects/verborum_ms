package de.coldtea.verborum.msmarketplace.dictionaryimport.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * One user's import of one listed dictionary (P4-07). Exists so `import_count` counts unique
 * importers: UNIQUE (fk_dictionary_id, fk_user_id) makes a repeat import find this row instead of
 * counting again, so nobody can inflate a dictionary's popularity by calling import repeatedly.
 */
@Getter
@Setter
@ToString
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "dictionary_imports")
public class DictionaryImport {

    // Server-generated: a system-owned record, the client never tracks its identity (like VaultEntry)
    @Id
    @Column(name = "import_id", updatable = false, nullable = false)
    private String importId;

    // Real FK to dictionary_stats with ON DELETE CASCADE — a same-service satellite of the listing,
    // with no life of its own. When reconciliation or user.deleted removes a listing, its import
    // records go with it
    @Column(name = "fk_dictionary_id", nullable = false)
    private String dictionaryId;

    // The importer's JWT subject (ms_user's keycloak_id)
    @Column(name = "fk_user_id", nullable = false)
    private String userId;

    @CreationTimestamp
    @Column(name = "imported_at", nullable = false, updatable = false)
    private OffsetDateTime importedAt;
}
