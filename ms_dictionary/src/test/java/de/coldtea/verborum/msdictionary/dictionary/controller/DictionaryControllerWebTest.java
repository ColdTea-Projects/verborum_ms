package de.coldtea.verborum.msdictionary.dictionary.controller;

import de.coldtea.verborum.msdictionary.common.config.SecurityConfig;
import de.coldtea.verborum.msdictionary.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msdictionary.common.exception.SharingRequiredException;
import de.coldtea.verborum.msdictionary.common.exception.GlobalExceptionHandler;
import de.coldtea.verborum.msdictionary.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryResponseDTO;
import de.coldtea.verborum.msdictionary.dictionary.service.DictionaryService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer smoke tests: the wiring the service-level unit tests cannot see — the security filter
 * chain, path variables, `@Valid`, and the mapping from a service exception to an HTTP status.
 * <p>
 * Added 2026-07-23 after a review pointed out that `contextLoads` was the only automated proof the
 * HTTP layer worked at all; everything else had been verified by hand with curl. Deliberately thin:
 * one case per behaviour that would be silently broken by a wiring mistake, not a re-test of the
 * service logic.
 */
@WebMvcTest(DictionaryController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class DictionaryControllerWebTest {

    private static final String SUB = "b87fb499-2002-47a7-b88f-8ae517932802";
    // Ids are validated as UUIDs since P4-09, so fixtures must use real ones
    private static final String DICTIONARY_ID = "5b0c9a3e-1f2d-4c7b-9e8a-6d5f4c3b2a10";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DictionaryService dictionaryService;

    /** The filter chain needs a decoder bean; the jwt() post-processor supplies the token itself. */
    @MockBean
    private JwtDecoder jwtDecoder;

    private static String body(String userId) {
        return """
                {"dictionaryId":"%s","userId":"%s","name":"Test","isPublic":false,
                 "fromLang":"EN","toLang":"DE"}
                """.formatted(DICTIONARY_ID, userId);
    }

    private static String bodyWith(String dictionaryId, String fromLang) {
        return """
                {"dictionaryId":"%s","userId":"%s","name":"Test","isPublic":false,
                 "fromLang":"%s","toLang":"DE"}
                """.formatted(dictionaryId, SUB, fromLang);
    }

    // ---- P4-09: @ValidUUID and @SupportedLanguage actually validate ----

    @Test
    void unsupportedLanguage_Is400AndNeverReachesTheService() throws Exception {
        mockMvc.perform(post("/dictionaries/")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith(DICTIONARY_ID, "XX")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorDetail").value("fromLang: unsupported language code"));

        verifyNoInteractions(dictionaryService);
    }

    @Test
    void lowercaseLanguage_IsStillAccepted() throws Exception {
        when(dictionaryService.saveDictionary(any(), anyString())).thenReturn(new DictionaryResponseDTO());

        mockMvc.perform(post("/dictionaries/")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith(DICTIONARY_ID, "en")))
                .andExpect(status().isCreated());
    }

    @Test
    void nonUuidDictionaryId_Is400AndNeverReachesTheService() throws Exception {
        mockMvc.perform(post("/dictionaries/")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("not-a-uuid", "EN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorDetail").value("dictionaryId: must be a valid UUID"));

        verifyNoInteractions(dictionaryService);
    }

    @Test
    void lenientlyParsedNonCanonicalUuid_Is400() throws Exception {
        // UUID.fromString would accept this; the validator must not
        mockMvc.perform(post("/dictionaries/")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("1-1-1-1-1", "EN")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dictionaryService);
    }

    @Test
    void unauthenticated_Is401() throws Exception {
        mockMvc.perform(get("/dictionaries/" + SUB))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedWrite_Is401() throws Exception {
        mockMvc.perform(post("/dictionaries/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(SUB)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRead_PassesTheTokenSubjectToTheService() throws Exception {
        when(dictionaryService.getDictionariesByUser(SUB)).thenReturn(List.of(new DictionaryResponseDTO()));

        mockMvc.perform(get("/dictionaries/" + SUB).with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk());
    }

    @Test
    void readingAnotherUsersList_Is403() throws Exception {
        // requireSelf runs in the controller, so this never reaches the service — exactly the
        // wiring a service-level test cannot cover
        mockMvc.perform(get("/dictionaries/someone-else").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidBody_Is400() throws Exception {
        mockMvc.perform(post("/dictionaries/")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dictionaryId":"d1","userId":"%s","name":"","isPublic":false,
                                 "fromLang":"EN","toLang":"DE"}
                                """.formatted(SUB)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serviceForbidden_MapsTo403() throws Exception {
        when(dictionaryService.saveDictionary(any(), anyString()))
                .thenThrow(new ForbiddenOperationException("nope"));

        mockMvc.perform(post("/dictionaries/")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(SUB)))
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceRecordNotFound_MapsTo404() throws Exception {
        when(dictionaryService.getDictionaryById(anyString(), anyString()))
                .thenThrow(new RecordNotFoundException("nope"));

        mockMvc.perform(get("/dictionaries/dictionary/d1").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isNotFound());
    }

    @Test
    void unhandledException_Is500_AndDoesNotLeakTheMessage() throws Exception {
        when(dictionaryService.getDictionariesByUser(SUB))
                .thenThrow(new IllegalStateException("duplicate key value violates constraint \"uq_secret\""));

        mockMvc.perform(get("/dictionaries/" + SUB).with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorDetail").value("Internal server error"));
    }

    @Test
    void sharingRuleBroken_MapsTo400WithTheRule() throws Exception {
        // P4-16: a member hiding their last shared dictionary
        when(dictionaryService.saveDictionary(any(), anyString()))
                .thenThrow(new SharingRequiredException("A marketplace member must keep at least one dictionary shared; leave the marketplace to make all of them private"));

        mockMvc.perform(put("/dictionaries/")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(SUB)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SharingRequiredException"));
    }
}
