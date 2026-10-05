package de.coldtea.verborum.msdictionary.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RequestBodyLimitFilterTest {

    private static final long LIMIT = 100;

    @Mock
    private FilterChain chain;

    private RequestBodyLimitFilter filter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new RequestBodyLimitFilter(LIMIT, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void doFilter_BodyOverTheLimit_Is413AndStopsTheChain() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/words");
        request.setContent(new byte[(int) LIMIT + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilter(request, response, chain);

        // Assert
        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"path\":\"/words\""));
        verifyNoInteractions(chain);
    }

    @Test
    void doFilter_ChunkedBodyWithoutLength_Is411AndStopsTheChain() throws Exception {
        // Arrange — a chunked body's size is only known after reading all of it
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/dictionaries/");
        request.addHeader("Transfer-Encoding", "chunked");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilter(request, response, chain);

        // Assert
        assertEquals(411, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void doFilter_BodyAtTheLimit_PassesThrough() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/words");
        request.setContent(new byte[(int) LIMIT]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilter(request, response, chain);

        // Assert
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_GetIsNeverChecked() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/words/batch");
        request.addHeader("Transfer-Encoding", "chunked");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilter(request, response, chain);

        // Assert
        verify(chain).doFilter(request, response);
    }
}
