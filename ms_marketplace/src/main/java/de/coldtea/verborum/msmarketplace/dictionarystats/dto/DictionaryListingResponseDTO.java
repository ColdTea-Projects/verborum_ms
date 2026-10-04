package de.coldtea.verborum.msmarketplace.dictionarystats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One marketplace listing as clients see it (P4-06).
 * <p>
 * `publisherId` is the owner's JWT subject — the value to pass to
 * `GET /marketplace/dictionaries/publisher/{publisherId}` for "more from this publisher". Exposing it
 * grants nothing: every service takes identity and ownership from the caller's token, never from a
 * supplied id. Show `publisherName` to users, never the id.
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

    // The publisher's display name (P4-13). Never null on a browse result: listings of a publisher
    // without a name are not returned
    private String publisherName;

    private String name;

    private String fromLang;

    private String toLang;

    private List<String> tags;

    private Integer importCount;

    private OffsetDateTime publishedAt;
}
