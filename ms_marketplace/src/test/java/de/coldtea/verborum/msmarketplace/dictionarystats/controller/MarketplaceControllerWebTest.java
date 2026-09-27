package de.coldtea.verborum.msmarketplace.dictionarystats.controller;

import de.coldtea.verborum.msmarketplace.common.config.SecurityConfig;
import de.coldtea.verborum.msmarketplace.common.exception.GlobalExceptionHandler;
import de.coldtea.verborum.msmarketplace.common.response.PageResponse;
import de.coldtea.verborum.msmarketplace.dictionarystats.dto.DictionaryListingResponseDTO;
import de.coldtea.verborum.msmarketplace.dictionarystats.service.DictionaryStatsService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer tests for marketplace browse (P4-06): the real security chain, the parameter validation
 * that turns bad input into 400s, and the paging envelope's JSON shape — the contract clients build
 * against.
 */
@WebMvcTest(MarketplaceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class MarketplaceControllerWebTest {

    private static final String SUB = "b87fb499-2002-47a7-b88f-8ae517932802";
    private static final String PUBLISHER = "78012064-231e-4a0d-abed-bad89a2350c1";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DictionaryStatsService dictionaryStatsService;

    /** The filter chain needs a decoder bean; the jwt() post-processor supplies the token itself. */
    @MockBean
    private JwtDecoder jwtDecoder;

    private static PageResponse<DictionaryListingResponseDTO> onePage() {
        return PageResponse.<DictionaryListingResponseDTO>builder()
                .items(List.of(DictionaryListingResponseDTO.builder()
                        .dictionaryId("dict1")
                        .publisherId(PUBLISHER)
                        .name("Travel")
                        .fromLang("EN")
                        .toLang("DE")
                        .importCount(3)
                        .publishedAt(OffsetDateTime.of(2026, 9, 27, 12, 0, 0, 0, ZoneOffset.UTC))
                        .build()))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .build();
    }

    @Test
    void unauthenticated_Is401() throws Exception {
        mockMvc.perform(get("/marketplace/dictionaries"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getListings_DefaultsAndEnvelopeShape() throws Exception {
        when(dictionaryStatsService.getListings(0, 20)).thenReturn(onePage());

        mockMvc.perform(get("/marketplace/dictionaries").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].dictionaryId").value("dict1"))
                .andExpect(jsonPath("$.items[0].publisherId").value(PUBLISHER))
                .andExpect(jsonPath("$.items[0].name").value("Travel"))
                .andExpect(jsonPath("$.items[0].importCount").value(3))
                .andExpect(jsonPath("$.items[0].publishedAt").exists())
                // internal state never reaches the client
                .andExpect(jsonPath("$.items[0].isListed").doesNotExist())
                .andExpect(jsonPath("$.items[0].sourceUpdatedAt").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
        // the stub only matches page=0, size=20, so a green run also proves the defaults
    }

    @Test
    void getPopularListings_PassesPaging() throws Exception {
        when(dictionaryStatsService.getPopularListings(1, 50)).thenReturn(onePage());

        mockMvc.perform(get("/marketplace/dictionaries/popular?page=1&size=50").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].dictionaryId").value("dict1"));
    }

    @Test
    void getListingsByLanguage_LowercaseCodesAccepted() throws Exception {
        when(dictionaryStatsService.getListingsByLanguage("en", "de", 0, 20)).thenReturn(onePage());

        mockMvc.perform(get("/marketplace/dictionaries/language?from=en&to=de").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk());
    }

    @Test
    void getListingsByLanguage_UnsupportedCode_Is400() throws Exception {
        mockMvc.perform(get("/marketplace/dictionaries/language?from=XX&to=DE").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("HandlerMethodValidationException"));

        verify(dictionaryStatsService, never()).getListingsByLanguage(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void getListingsByLanguage_MissingParameter_Is400() throws Exception {
        mockMvc.perform(get("/marketplace/dictionaries/language?from=EN").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MissingServletRequestParameterException"));
    }

    @Test
    void getListings_SizeAboveMax_Is400() throws Exception {
        mockMvc.perform(get("/marketplace/dictionaries?size=101").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getListings_SizeZero_Is400() throws Exception {
        mockMvc.perform(get("/marketplace/dictionaries?size=0").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getListings_NegativePage_Is400() throws Exception {
        mockMvc.perform(get("/marketplace/dictionaries?page=-1").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getListings_NonNumericPage_Is400AndDoesNotEchoTheInput() throws Exception {
        mockMvc.perform(get("/marketplace/dictionaries?page=abc").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorDetail").value("Invalid value for parameter: page"));
    }

    @Test
    void getListingsByPublisher_PassesThePathIdNotTheCaller() throws Exception {
        // Browsing someone else's listings is the point — the caller's own subject must not be substituted
        when(dictionaryStatsService.getListingsByPublisher(PUBLISHER, 0, 20)).thenReturn(onePage());

        mockMvc.perform(get("/marketplace/dictionaries/publisher/" + PUBLISHER).with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].publisherId").value(PUBLISHER));
    }
}
