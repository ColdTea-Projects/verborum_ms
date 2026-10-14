package de.coldtea.verborum.msnotification.devicetoken.controller;

import de.coldtea.verborum.msnotification.common.response.Response;
import de.coldtea.verborum.msnotification.common.utils.ValidUUID;
import de.coldtea.verborum.msnotification.devicetoken.dto.DeviceRequestDTO;
import de.coldtea.verborum.msnotification.devicetoken.service.DeviceTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import static de.coldtea.verborum.msnotification.common.constants.ResponseMessageConstants.DEVICE_REGISTERED_SUCCESSFULLY;
import static de.coldtea.verborum.msnotification.common.constants.ResponseMessageConstants.DEVICE_REMOVED_SUCCESSFULLY;
import static de.coldtea.verborum.msnotification.common.utils.ResponseUtils.buildResponse;
import static de.coldtea.verborum.msnotification.common.utils.SecurityUtils.getCurrentUserId;

/**
 * Device registration for push (P4N-03). Owner only: every call acts on the token subject's own
 * devices, and there is no read endpoint — a token is a delivery address, not data a client needs back.
 */
@RestController
@RequestMapping("/notifications/devices")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    @PutMapping
    public ResponseEntity<Response> registerDevice(@Valid @RequestBody DeviceRequestDTO device, WebRequest request) {
        deviceTokenService.registerDevice(device, getCurrentUserId());
        return buildResponse(HttpStatus.CREATED, DEVICE_REGISTERED_SUCCESSFULLY, device.getDeviceId(), request);
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Response> removeDevice(@PathVariable @ValidUUID String deviceId, WebRequest request) {
        deviceTokenService.removeDevice(deviceId, getCurrentUserId());
        return buildResponse(HttpStatus.OK, DEVICE_REMOVED_SUCCESSFULLY, deviceId, request);
    }
}
