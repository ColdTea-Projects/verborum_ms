package de.coldtea.verborum.msmarketplace.dictionarystats.controller;

import de.coldtea.verborum.msmarketplace.common.config.SecurityConfig;
import de.coldtea.verborum.msmarketplace.common.exception.GlobalExceptionHandler;
import de.coldtea.verborum.msmarketplace.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msmarketplace.common.exception.SelfImportException;
import de.coldtea.verborum.msmarketplace.common.response.SliceResponse;
import de.coldtea.verborum.msmarketplace.dictionaryimport.service.DictionaryImportService;
import de.coldtea.verborum.msmarketplace.dictionarystats.dto.DictionaryListingResponseDTO;
import de.coldtea.verborum.msmarketplace.dictionarystats.dto.ListingFilter;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer tests for marketplace browse (P4-06, P4-11): the real security chain, the parameter validation
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

    @MockBean
    private DictionaryImportService dictionaryImportService;

    /** The filter chain needs a decoder bean; the jwt() post-processor supplies the token itself. */
    @MockBean
    private JwtDecoder jwtDecoder;

    private static SliceResponse<DictionaryListingResponseDTO> oneSlice() {
        return SliceResponse.<DictionaryListingResponseDTO>builder()
                .items(List.of(DictionaryListingResponseDTO.builder()
                        .dictionaryId("dict1")
                        .publisherId(PUBLISHER)
                        .name("Travel")
                        .fromLang("EN")
                        .toLang("DE")
                        .tags(List.of("food", "travel"))
                        .importCount(3)
                        .publishedAt(OffsetDateTime.of(2026, 9, 27, 12, 0, 0, 0, ZoneOffset.UTC))
                        .build()))
                .page(0)
                .size(20)
                .hasNext(true)
                .build();
    }

    @Test
    void unauthenticated_Is401() throws Exception {
        mockMvc.perform(get("/marketplace/dictionaries"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getListings_DefaultsAndEnvelopeShape() throws Exception {
        when(dictionaryStatsService.getListings(ListingFilter.NONE, 0, 20)).thenReturn(oneSlice());

        mockMvc.perform(get("/marketplace/dictionaries").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].dictionaryId").value("dict1"))
                .andExpect(jsonPath("$.items[0].publisherId").value(PUBLISHER))
                .andExpect(jsonPath("$.items[0].name").value("Travel"))
                .andExpect(jsonPath("$.items[0].importCount").value(3))
                .andExpect(jsonPath("$.items[0].tags[0]").value("food"))
                .andExpect(jsonPath("$.items[0].tags[1]").value("travel"))
                .andExpect(jsonPath("$.items[0].publishedAt").exists())
                // internal state never reaches the client
                .andExpect(jsonPath("$.items[0].isListed").doesNotExist())
                .andExpect(jsonPath("$.items[0].sourceUpdatedAt").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.hasNext").value(true))
                // infinite scroll (P4-11): no totals, so no count query behind them
                .andExpect(jsonPath("$.totalElements").doesNotExist())
                .andExpect(jsonPath("$.totalPages").doesNotExist());
        // the stub only matches page=0, size=20, so a green run also proves the defaults
    }

    @Test
    void getPopularListings_PassesPaging() throws Exception {
        when(dictionaryStatsService.getPopularListings(ListingFilter.NONE, 1, 50)).thenReturn(oneSlice());

        mockMvc.perform(get("/marketplace/dictionaries/popular?page=1&size=50").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].dictionaryId").value("dict1"));
    }

    // ---- language-pair filter (P4-11) ----

    @Test
    void getListings_RepeatedPairs_PassedAsSentInAnyCase() throws Exception {
        // Normalising is the service's job; the controller hands over what was validated
        when(dictionaryStatsService.getListings(new ListingFilter(List.of("en-tr", "FR-DE"), null), 0, 20)).thenReturn(oneSlice());

        mockMvc.perform(get("/marketplace/dictionaries?pair=en-tr&pair=FR-DE").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].dictionaryId").value("dict1"));
    }

    @Test
    void getListings_CommaSeparatedPairs_AlsoAccepted() throws Exception {
        when(dictionaryStatsService.getListings(new ListingFilter(List.of("EN-TR", "FR-DE"), null), 0, 20)).thenReturn(oneSlice());

        mockMvc.perform(get("/marketplace/dictionaries?pair=EN-TR,FR-DE").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].dictionaryId").value("dict1"));
    }

    @Test
    void getPopularListings_PairFilter_Passed() throws Exception {
        when(dictionaryStatsService.getPopularListings(new ListingFilter(List.of("DE-TR"), null), 0, 20)).thenReturn(oneSlice());

        mockMvc.perform(get("/marketplace/dictionaries/popular?pair=DE-TR").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].dictionaryId").value("dict1"));
    }

    @Test
    void getListings_UnsupportedLanguageInPair_Is400() throws Exception {
        mockMvc.perform(get("/marketplace/dictionaries?pair=EN-TR&pair=EN-XX").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("HandlerMethodValidationException"))
                .andExpect(jsonPath("$.errorDetail").value("pair: must be two different supported language codes, e.g. EN-TR"));

        verify(dictionaryStatsService, never()).getListings(any(), anyInt(), anyInt());
    }

    @Test
    void getPopularListings_SameLanguageTwice_Is400() throws Exception {
        mockMvc.perform(get("/marketplace/dictionaries/popular?pair=EN-EN").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("HandlerMethodValidationException"));

        verify(dictionaryStatsService, never()).getPopularListings(any(), anyInt(), anyInt());
    }

    @Test
    void getListings_MalformedPair_Is400() throws Exception {
        mockMvc.perform(get("/marketplace/dictionaries?pair=ENTR").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("HandlerMethodValidationException"));
    }

    @Test
    void getListings_MoreThanTenPairs_Is400() throws Exception {
        String pairs = "pair=EN-DE&pair=EN-FR&pair=EN-ES&pair=EN-IT&pair=EN-PT&pair=EN-NL"
                + "&pair=EN-TR&pair=EN-AZ&pair=EN-LT&pair=EN-PL&pair=EN-UK";

        mockMvc.perform(get("/marketplace/dictionaries?" + pairs).with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorDetail").value("pair: at most 10 language pairs"));

        verify(dictionaryStatsService, never()).getListings(any(), anyInt(), anyInt());
    }

    // ---- tag filter (P4-12) ----

    @Test
    void getListings_TagsAndPairs_PassedTogether() throws Exception {
        when(dictionaryStatsService.getListings(new ListingFilter(List.of("EN-TR"), List.of("Food", "travel")), 0, 20))
                .thenReturn(oneSlice());

        mockMvc.perform(get("/marketplace/dictionaries?pair=EN-TR&tag=Food&tag=travel").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].dictionaryId").value("dict1"));
    }

    @Test
    void getPopularListings_TagFilter_Passed() throws Exception {
        when(dictionaryStatsService.getPopularListings(new ListingFilter(null, List.of("food")), 0, 20)).thenReturn(oneSlice());

        mockMvc.perform(get("/marketplace/dictionaries/popular?tag=food").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].dictionaryId").value("dict1"));
    }

    @Test
    void getListings_BlankTag_Is400() throws Exception {
        // .param, not the URL template: the template would re-encode "%20" and send a non-blank value
        mockMvc.perform(get("/marketplace/dictionaries").param("tag", "food", " ").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("HandlerMethodValidationException"))
                .andExpect(jsonPath("$.errorDetail").value("tag: must not be blank"));

        verify(dictionaryStatsService, never()).getListings(any(), anyInt(), anyInt());
    }

    @Test
    void getListings_TagTooLong_Is400() throws Exception {
        mockMvc.perform(get("/marketplace/dictionaries?tag=" + "a".repeat(101)).with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorDetail").value("tag: must be at most 100 characters"));
    }

    @Test
    void getPopularListings_MoreThanTenTags_Is400() throws Exception {
        StringBuilder tags = new StringBuilder("tag=t0");
        for (int i = 1; i <= 10; i++) {
            tags.append("&tag=t").append(i);
        }

        mockMvc.perform(get("/marketplace/dictionaries/popular?" + tags).with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorDetail").value("tag: at most 10 tags"));

        verify(dictionaryStatsService, never()).getPopularListings(any(), anyInt(), anyInt());
    }

    @Test
    void getListingsByLanguage_EndpointRemoved_Is404() throws Exception {
        // Replaced by ?pair= (P4-11); a client still calling it must fail visibly, not get everything
        mockMvc.perform(get("/marketplace/dictionaries/language?from=EN&to=DE").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isNotFound());
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

    // ---- import (P4-07) ----

    @Test
    void importDictionary_Unauthenticated_Is401() throws Exception {
        mockMvc.perform(post("/marketplace/dictionaries/dict1/import"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(dictionaryImportService);
    }

    @Test
    void importDictionary_ImporterIsTheTokenSubject_Is201() throws Exception {
        mockMvc.perform(post("/marketplace/dictionaries/dict1/import").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Imported successfully dictionary dict1"));

        verify(dictionaryImportService).importDictionary("dict1", SUB);
    }

    @Test
    void importDictionary_HiddenOrUnknownListing_Is404() throws Exception {
        doThrow(new RecordNotFoundException("Listing was not found. ID: dict1"))
                .when(dictionaryImportService).importDictionary("dict1", SUB);

        mockMvc.perform(post("/marketplace/dictionaries/dict1/import").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isNotFound());
    }

    @Test
    void importDictionary_OwnDictionary_Is400() throws Exception {
        doThrow(new SelfImportException("A dictionary cannot be imported by its own publisher"))
                .when(dictionaryImportService).importDictionary("dict1", SUB);

        mockMvc.perform(post("/marketplace/dictionaries/dict1/import").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SelfImportException"));
    }

    @Test
    void getListingsByPublisher_PassesThePathIdNotTheCaller() throws Exception {
        // Browsing someone else's listings is the point — the caller's own subject must not be substituted
        when(dictionaryStatsService.getListingsByPublisher(PUBLISHER, 0, 20)).thenReturn(oneSlice());

        mockMvc.perform(get("/marketplace/dictionaries/publisher/" + PUBLISHER).with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].publisherId").value(PUBLISHER));
    }
}
