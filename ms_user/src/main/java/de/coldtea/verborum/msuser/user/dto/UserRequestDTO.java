package de.coldtea.verborum.msuser.user.dto;

import de.coldtea.verborum.msuser.common.utils.ValidUUID;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import static de.coldtea.verborum.msuser.common.constants.DTOMessageConstants.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRequestDTO {

    @NotBlank(message = USER_USER_ID)
    @ValidUUID(fieldName = USER_ID)
    private String userId;

    @NotBlank(message = USER_KEYCLOAK_ID)
    @ValidUUID(fieldName = KEYCLOAK_ID)
    private String keycloakId;

    @NotBlank(message = USER_EMAIL)
    @Email(message = USER_EMAIL_INVALID)
    private String email;

    private String displayName;

}
