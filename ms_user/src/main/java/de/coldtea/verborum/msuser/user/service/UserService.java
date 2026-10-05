package de.coldtea.verborum.msuser.user.service;

import de.coldtea.verborum.msuser.user.dto.ProfileInfoRequestDTO;
import de.coldtea.verborum.msuser.user.dto.ProfileResponseDTO;
import de.coldtea.verborum.msuser.user.dto.UserRequestDTO;
import de.coldtea.verborum.msuser.user.dto.UserResponseDTO;

/**
 * Every method takes the caller's `callerKeycloakId` (the JWT subject) and refuses to touch a
 * profile belonging to anyone else — P3-05. It is passed in rather than read from the security
 * context so the services stay plain objects, testable without a SecurityContextHolder.
 */
public interface UserService {
    UserResponseDTO saveUser(UserRequestDTO userDto, String callerKeycloakId, String callerEmail);
    UserResponseDTO getUserById(String userId, String callerKeycloakId);
    void deleteUser(String userId, String callerKeycloakId);

    /** The caller's own profile, by token subject (P4-14). 404 when they have none yet. */
    ProfileResponseDTO getMyProfile(String callerKeycloakId);

    /**
     * Partial update of the caller's display name and marketplace agreement (P4-14). Absent fields are
     * left as they are. 400 when the result would have accepted terms without a display name, or an
     * acceptance without a terms version; 404 when the caller has no profile.
     */
    ProfileResponseDTO updateProfileInfo(ProfileInfoRequestDTO profileInfo, String callerKeycloakId);
}
