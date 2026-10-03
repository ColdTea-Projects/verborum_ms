package de.coldtea.verborum.msdictionary.common.utils;

import de.coldtea.verborum.msdictionary.common.response.Response;
import de.coldtea.verborum.msdictionary.word.dto.WordBundleRequestDTO;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

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

    /**
     * The detail for the word save/update messages ("Saved successfully into dictionary "): the
     * dictionaries the bundles targeted, each once. It used to be the saved words themselves — which
     * did not match the message, and since a word is a JSON array of surfaces, a large batch echoed its
     * whole payload back in the response.
     */
    public static String getDictionaryIds(List<WordBundleRequestDTO> bundles) {
        return bundles.stream()
                .map(WordBundleRequestDTO::getDictionaryId)
                .distinct()
                .collect(Collectors.joining(", "));
    }
}
