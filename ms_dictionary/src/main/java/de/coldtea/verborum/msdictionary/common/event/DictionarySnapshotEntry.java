package de.coldtea.verborum.msdictionary.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * One public dictionary inside a {@link DictionarySnapshotEvent} — the same listing fields the
 * visibility and update events carry, so the consumer can build or correct a listing from it alone.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionarySnapshotEntry {

    private String dictionaryId;

    private String userId;

    private String fromLang;

    private String toLang;

    private String dictionaryName;

    private OffsetDateTime updatedAt;
}
