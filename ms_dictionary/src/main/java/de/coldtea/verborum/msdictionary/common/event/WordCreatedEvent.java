package de.coldtea.verborum.msdictionary.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Published on `word.created`, once per newly created word. Consumed by ms_autofil (V2) to
 * aggregate community translations by language pair.
 * <p>
 * `userId`, `fromLang` and `toLang` are not stored on `Word` — they are carried over from the
 * word's `Dictionary` so a consumer can bucket by language pair without calling back.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WordCreatedEvent {

    private String wordId;

    private String dictionaryId;

    private String userId;

    private String word;

    private String translation;

    private String fromLang;

    private String toLang;

    private LocalDateTime eventTimestamp;
}
