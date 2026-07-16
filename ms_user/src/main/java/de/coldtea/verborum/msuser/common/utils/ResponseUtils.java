package de.coldtea.verborum.msuser.common.utils;

import de.coldtea.verborum.msuser.common.response.Response;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.time.OffsetDateTime;

@AllArgsConstructor
public class ResponseUtils {
    public static ResponseEntity<Response> buildResponse(HttpStatus status, String message, String detail, WebRequest request) {
        return new ResponseEntity<>(Response.builder()
                .status(status.value())
                .message(message + detail)
                .path(extractPath(request))
                .timestamp(OffsetDateTime.now())
                .build(), status);
    }

    public static String extractPath(WebRequest request) {
        return request.getDescription(false).replaceFirst("^uri=", "");
    }
}
