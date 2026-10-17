package de.coldtea.verborum.msgateway.common.exception;

import de.coldtea.verborum.msgateway.common.response.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.ServletWebRequest;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("GET", "/users/me"));

    @Test
    void handleResourceAccessException_Timeout_Is504() {
        // Arrange
        ResourceAccessException ex = new ResourceAccessException("I/O error on GET request for \"http://user:8086/users/me\"",
                new SocketTimeoutException("Read timed out"));

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleResourceAccessException(ex, request);

        // Assert
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        assertEquals("The service did not answer in time", response.getBody().getErrorDetail());
    }

    @Test
    void handleResourceAccessException_ConnectionRefused_Is502_WithoutTheHost() {
        // Arrange
        ResourceAccessException ex = new ResourceAccessException("I/O error on GET request for \"http://user:8086/users/me\"",
                new ConnectException("Connection refused"));

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleResourceAccessException(ex, request);

        // Assert
        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertFalse(response.getBody().getErrorDetail().contains("8086"));
    }

    @Test
    void handleException_NeverEchoesTheMessage() {
        // Act
        ResponseEntity<ErrorResponse> response = handler.handleException(new IOException("secret internals"), request);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal server error", response.getBody().getErrorDetail());
    }
}
