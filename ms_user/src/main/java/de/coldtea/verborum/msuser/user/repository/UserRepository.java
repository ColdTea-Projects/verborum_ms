package de.coldtea.verborum.msuser.user.repository;

import de.coldtea.verborum.msuser.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    // keycloak_id is the cross-service join key (other services' fk_user_id is the JWT subject),
    // so inbound events identify a user this way rather than by ms_user's own user_id.
    Optional<User> findByKeycloakId(String keycloakId);
}
