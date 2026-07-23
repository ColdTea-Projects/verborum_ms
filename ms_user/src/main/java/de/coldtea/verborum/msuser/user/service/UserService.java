package de.coldtea.verborum.msuser.user.service;

import de.coldtea.verborum.msuser.user.dto.UserRequestDTO;
import de.coldtea.verborum.msuser.user.dto.UserResponseDTO;

/**
 * Every method takes the caller's `callerKeycloakId` (the JWT subject) and refuses to touch a
 * profile belonging to anyone else — P3-05. It is passed in rather than read from the security
 * context so the services stay plain objects, testable without a SecurityContextHolder.
 */
public interface UserService {
    UserResponseDTO saveUser(UserRequestDTO userDto, String callerKeycloakId);
    UserResponseDTO getUserById(String userId, String callerKeycloakId);
    void deleteUser(String userId, String callerKeycloakId);
}
