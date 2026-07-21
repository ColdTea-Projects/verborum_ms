package de.coldtea.verborum.msuser.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;

import static de.coldtea.verborum.msuser.common.constants.DTOMessageConstants.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDTO {

    @NotBlank(message = USER_USER_ID)
    private String userId;

    @NotBlank(message = USER_KEYCLOAK_ID)
    private String keycloakId;

    @NotBlank(message = USER_EMAIL)
    private String email;

    private String displayName;

    private LocalDateTime creationTimestamp;

    private LocalDateTime updateTimestamp;

}
