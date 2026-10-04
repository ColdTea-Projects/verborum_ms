package de.coldtea.verborum.msuser.user.dto;

import de.coldtea.verborum.msuser.common.utils.ValidUUID;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import static de.coldtea.verborum.msuser.common.constants.DTOMessageConstants.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRequestDTO {

    @NotBlank(message = USER_USER_ID)
    @ValidUUID
    private String userId;

    @NotBlank(message = USER_KEYCLOAK_ID)
    @ValidUUID
    private String keycloakId;

    @NotBlank(message = USER_EMAIL)
    @Email(message = USER_EMAIL_INVALID)
    private String email;

    // Optional: a user who never uses the marketplace needs no name. The clients require one before
    // marketplace use; the marketplace hides the listings of a publisher without one (P4-13)
    @Size(max = USER_DISPLAY_NAME_MAX, message = USER_DISPLAY_NAME_TOO_LONG)
    private String displayName;

}
