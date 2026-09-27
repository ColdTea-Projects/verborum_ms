package de.coldtea.verborum.msdictionary.word.controller;

import de.coldtea.verborum.msdictionary.common.config.SecurityConfig;
import de.coldtea.verborum.msdictionary.common.exception.GlobalExceptionHandler;
import de.coldtea.verborum.msdictionary.word.service.WordService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for the validation P4-09 made real: @ValidUUID and @SupportedLanguage on path
 * variables, and constraint failures inside the list request body of `POST /words` — which, before
 * P4-09, fell through to the catch-all as a 500.
 */
@WebMvcTest(WordController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class WordControllerWebTest {

    private static final String SUB = "b87fb499-2002-47a7-b88f-8ae517932802";
    private static final String DICTIONARY_ID = "5b0c9a3e-1f2d-4c7b-9e8a-6d5f4c3b2a10";
    private static final String WORD_ID = "7c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WordService wordService;

    /** The filter chain needs a decoder bean; the jwt() post-processor supplies the token itself. */
    @MockBean
    private JwtDecoder jwtDecoder;

    private static String bundle(String wordId, String word) {
        return """
                [{"dictionaryId":"%s","words":[{"wordId":"%s","word":"%s","wordMeta":"{}",
                  "translation":"house","translationMeta":"{}"}]}]
                """.formatted(DICTIONARY_ID, wordId, word);
    }

    @Test
    void unauthenticated_Is401() throws Exception {
        mockMvc.perform(get("/words/dictionary/" + DICTIONARY_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteWord_NonUuidPathVariable_Is400AndNeverReachesTheService() throws Exception {
        mockMvc.perform(delete("/words/not-a-uuid").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("HandlerMethodValidationException"))
                .andExpect(jsonPath("$.errorDetail").value("wordId: must be a valid UUID"));

        verifyNoInteractions(wordService);
    }

    @Test
    void deleteWord_ValidUuid_ReachesTheService() throws Exception {
        mockMvc.perform(delete("/words/" + WORD_ID).with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk());

        verify(wordService).deleteWords(List.of(WORD_ID), SUB);
    }

    @Test
    void getWordsByLanguageFrom_UnsupportedLanguage_Is400() throws Exception {
        mockMvc.perform(get("/words/language/from/XX").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorDetail").value("language: unsupported language code"));

        verifyNoInteractions(wordService);
    }

    @Test
    void getWordsByLanguageFrom_LowercaseLanguage_IsAccepted() throws Exception {
        when(wordService.getWordsByLanguageFrom(anyString(), anyString())).thenReturn(List.of());

        mockMvc.perform(get("/words/language/from/de").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk());
    }

    @Test
    void createWords_BlankWordInsideTheList_Is400Not500() throws Exception {
        mockMvc.perform(post("/words")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle(WORD_ID, "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("HandlerMethodValidationException"))
                // the nested path is what lets a client find the bad entry in a batch
                .andExpect(jsonPath("$.errorDetail").value("bundles.words[0].word: word is mandatory"));

        verifyNoInteractions(wordService);
    }

    @Test
    void createWords_NonUuidWordIdInsideTheList_Is400() throws Exception {
        mockMvc.perform(post("/words")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle("not-a-uuid", "Haus")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(wordService);
    }

    @Test
    void createWords_ValidBundle_Is201() throws Exception {
        mockMvc.perform(post("/words")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle(WORD_ID, "Haus")))
                .andExpect(status().isCreated());
    }
}
