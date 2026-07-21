package de.coldtea.verborum.msuser.user.repository;

import de.coldtea.verborum.msuser.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
}
