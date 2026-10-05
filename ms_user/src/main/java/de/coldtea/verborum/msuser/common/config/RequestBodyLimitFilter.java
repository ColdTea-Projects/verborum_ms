package de.coldtea.verborum.msuser.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.coldtea.verborum.msuser.common.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Set;

import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.REQUEST_BODY_LENGTH_REQUIRED;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.REQUEST_BODY_TOO_LARGE;

/**
 * SEC-07: refuses a request body above `verborum.request.max-body-bytes` before Jackson reads it. Field
 * and collection limits only apply after the whole body is parsed into memory, so on their own they
 * still let one request hold megabytes per thread.
 * <p>
 * A body with no declared length (chunked transfer) is refused with 411, because its size is only
 * known after reading it. No client is affected: OkHttp, Ktor and browsers all send `Content-Length`
 * for a serialised JSON body.
 * <p>
 * Runs first, ahead of the security chain, so an oversized body is refused before any work is done.
 * It answers in the usual error envelope itself — a `sendError` would go through `/error`, which the
 * security chain guards, and come back as a misleading 401.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    private final long maxBodyBytes;

    private final ObjectMapper objectMapper;

    public RequestBodyLimitFilter(@Value("${verborum.request.max-body-bytes}") long maxBodyBytes, ObjectMapper objectMapper) {
        this.maxBodyBytes = maxBodyBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (BODY_METHODS.contains(request.getMethod())) {
            long length = request.getContentLengthLong();
            if (length > maxBodyBytes) {
                reject(request, response, HttpStatus.PAYLOAD_TOO_LARGE, REQUEST_BODY_TOO_LARGE + maxBodyBytes + " bytes");
                return;
            }
            if (length < 0 && request.getHeader("Transfer-Encoding") != null) {
                reject(request, response, HttpStatus.LENGTH_REQUIRED, REQUEST_BODY_LENGTH_REQUIRED);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .errorDetail(detail)
                .path(request.getRequestURI())
                .timestamp(OffsetDateTime.now())
                .build());
    }
}
