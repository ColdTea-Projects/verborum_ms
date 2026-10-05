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
import static java.util.Collections.nCopies;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void createWords_ValidBundle_Is201AndNamesTheDictionary() throws Exception {
        // The message says "into dictionary" — it used to be followed by the saved words instead
        mockMvc.perform(post("/words")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundle(WORD_ID, "Haus")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Saved successfully into dictionary " + DICTIONARY_ID));
    }

    @Test
    void updateWords_TwoBundlesSameDictionary_NamesItOnce() throws Exception {
        String twoBundles = """
                [{"dictionaryId":"%1$s","words":[{"wordId":"%2$s","word":"Haus","wordMeta":"{}",
                  "translation":"house","translationMeta":"{}"}]},
                 {"dictionaryId":"%1$s","words":[{"wordId":"%3$s","word":"Baum","wordMeta":"{}",
                  "translation":"tree","translationMeta":"{}"}]}]
                """.formatted(DICTIONARY_ID, WORD_ID, "8d2e3f4a-5b6c-4d7e-9f8a-0b1c2d3e4f5a");

        mockMvc.perform(put("/words")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoBundles))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Updated successfully into dictionary " + DICTIONARY_ID));
    }

    // ---- SEC-07: collection limits ----

    @Test
    void createWords_TooManyBundles_Is400AndNeverReachesTheService() throws Exception {
        String oneBundle = bundle(WORD_ID, "Haus").trim();
        String bundles = "[" + String.join(",", nCopies(6, oneBundle.substring(1, oneBundle.length() - 1))) + "]";

        mockMvc.perform(post("/words").with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON).content(bundles))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorDetail").value(containsString("at most 5 bundles per request")));

        verifyNoInteractions(wordService);
    }

    @Test
    void createWords_TooManyWordsInABundle_Is400AndNeverReachesTheService() throws Exception {
        String word = """
                {"wordId":"%s","word":"w","wordMeta":"{}","translation":"t","translationMeta":"{}"}""".formatted(WORD_ID);
        String body = "[{\"dictionaryId\":\"%s\",\"words\":[%s]}]"
                .formatted(DICTIONARY_ID, String.join(",", nCopies(501, word)));

        mockMvc.perform(post("/words").with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorDetail").value(containsString("at most 500 words per bundle")));

        verifyNoInteractions(wordService);
    }

    @Test
    void getWordsByIds_TooManyIds_Is400AndNeverReachesTheService() throws Exception {
        String ids = String.join(",", nCopies(101, WORD_ID));

        mockMvc.perform(get("/words/batch").param("ids", ids).with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorDetail").value(containsString("at most 100 ids per request")));

        verifyNoInteractions(wordService);
    }

    @Test
    void createWords_BodyOverTheLimit_Is413() throws Exception {
        // RequestBodyLimitFilter is a @Component, so the web slice runs it; 2 MB + 1 from application.properties
        mockMvc.perform(post("/words").with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON).content(new byte[2 * 1024 * 1024 + 1]))
                .andExpect(status().isPayloadTooLarge());

        verifyNoInteractions(wordService);
    }
}
