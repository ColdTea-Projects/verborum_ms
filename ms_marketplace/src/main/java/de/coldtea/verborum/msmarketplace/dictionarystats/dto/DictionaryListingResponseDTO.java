package de.coldtea.verborum.msmarketplace.dictionarystats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * One marketplace listing as clients see it (P4-06).
 * <p>
 * `publisherId` is the owner's JWT subject — the value to pass to
 * `GET /marketplace/dictionaries/publisher/{publisherId}` for "more from this publisher". Exposing it
 * grants nothing: every service takes identity and ownership from the caller's token, never from a
 * supplied id. There is no display name yet; that needs ms_user data (roadmap BL-04).
 * <p>
 * `isListed` and `sourceUpdatedAt` are deliberately absent — hidden rows are never returned, and the
 * ordering key is internal.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DictionaryListingResponseDTO {

    private String dictionaryId;

    private String publisherId;

    private String name;

    private String fromLang;

    private String toLang;

    private Integer importCount;

    private OffsetDateTime publishedAt;
}
