package de.coldtea.verborum.msnotification.devicetoken.dto;

import de.coldtea.verborum.msnotification.common.utils.ValidUUID;
import de.coldtea.verborum.msnotification.devicetoken.entity.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static de.coldtea.verborum.msnotification.common.constants.DTOMessageConstants.*;

/** `PUT /notifications/devices` (P4N-03) — registers or refreshes one app install. The owner is the token subject. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceRequestDTO {

    @NotBlank(message = DEVICE_ID_REQUIRED)
    @ValidUUID
    private String deviceId;

    @NotBlank(message = TOKEN_REQUIRED)
    @Size(max = TOKEN_MAX_LENGTH, message = TOKEN_TOO_LONG)
    private String token;

    @NotNull(message = PLATFORM_REQUIRED)
    private DevicePlatform platform;

    @Size(max = LOCALE_MAX_LENGTH, message = LOCALE_TOO_LONG)
    private String locale;

    @Size(max = APP_VERSION_MAX_LENGTH, message = APP_VERSION_TOO_LONG)
    private String appVersion;
}
