package de.coldtea.verborum.msmarketplace.common.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The paging envelope for list reads (P4-11, replacing P4-06's `PageResponse`). Built for infinite
 * scroll: it says whether another page exists, not how many there are. Totals would cost a second,
 * fully filtered `COUNT(*)` on every request — and a marketplace that re-queries on every filter
 * change would pay it each time for a number the client never shows.
 * <p>
 * Our own shape on purpose rather than Spring's serialized `Slice`, which is an implementation detail
 * Spring Data flags as unstable across versions. `page` is zero-based, matching the request parameter.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SliceResponse<T> {

    private List<T> items;

    private int page;

    private int size;

    private boolean hasNext;
}
