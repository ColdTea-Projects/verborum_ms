package de.coldtea.verborum.msmarketplace.common.utils;

import de.coldtea.verborum.msmarketplace.common.response.Response;
import de.coldtea.verborum.msmarketplace.common.response.SliceResponse;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Slice;
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

    /** Copies a Spring `Slice` into the public paging envelope; see {@link SliceResponse}. */
    public static <T> SliceResponse<T> toSliceResponse(Slice<T> slice) {
        return SliceResponse.<T>builder()
                .items(slice.getContent())
                .page(slice.getNumber())
                .size(slice.getSize())
                .hasNext(slice.hasNext())
                .build();
    }
}
