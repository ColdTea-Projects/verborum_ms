package de.coldtea.verborum.msuser.userstats.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_stats")
public class UserStats {
    // PK and FK to users.user_id (1:1). The relationship is not mapped as a JPA association
    // (project convention — no @OneToOne/@ManyToOne; joins go through repositories); the FK is
    // enforced at the DB with ON DELETE CASCADE so a user's stats row is removed with the user.
    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private String userId;

    @Column(name = "total_words")
    private Integer totalWords;

    @Column(name = "total_dictionaries")
    private Integer totalDictionaries;

    @UpdateTimestamp
    @Column(name = "update_dt")
    private LocalDateTime updateTimestamp;
}
