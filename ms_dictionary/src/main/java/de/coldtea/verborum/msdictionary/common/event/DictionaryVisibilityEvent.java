package de.coldtea.verborum.msdictionary.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Published on `dictionary.visibility.public` / `dictionary.visibility.private` when a
 * dictionary's `is_public` flag changes. Consumed by ms_marketplace to create or remove the
 * marketplace listing — it carries the full listing payload so the consumer does not have to
 * call back into ms_dictionary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionaryVisibilityEvent {

    private String dictionaryId;

    private String userId;

    private Boolean isPublic;

    private String fromLang;

    private String toLang;

    private String dictionaryName;

    private OffsetDateTime eventTimestamp;
}
