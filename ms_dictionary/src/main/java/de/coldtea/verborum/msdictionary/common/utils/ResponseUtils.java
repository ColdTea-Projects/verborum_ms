package de.coldtea.verborum.msdictionary.common.utils;

import de.coldtea.verborum.msdictionary.common.response.Response;
import de.coldtea.verborum.msdictionary.word.dto.WordRequestDTO;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@NoArgsConstructor
public class ResponseUtils {
    public static ResponseEntity<Response> buildResponse(HttpStatus status, String message, String dictionaryOrWord, WebRequest request) {
        return new ResponseEntity<>(Response.builder()
                .status(status.value())
                .message(message + dictionaryOrWord)
                .path(request.getContextPath())
                .timestamp(OffsetDateTime.now())
                .build(), status);
    }

    public static String getListOfWords(List<WordRequestDTO> words) {
        return words.stream()
                .map(WordRequestDTO::getWord)
                .collect(Collectors.joining(", "));
    }
}
