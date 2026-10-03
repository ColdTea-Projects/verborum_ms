package de.coldtea.verborum.msmarketplace.common.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The paging envelope for list reads (P4-06, decided 2026-09-27) — the first paginated contract in
 * Verborum. Our own shape on purpose rather than Spring's serialized `Page`: that JSON is an
 * implementation detail of `PageImpl` (~15 fields, some nested) which Spring Data itself flags as
 * unstable across versions, and three client apps build against this.
 * <p>
 * `page` is zero-based, matching the `page` request parameter.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PageResponse<T> {

    private List<T> items;

    private int page;

    private int size;

    private long totalElements;

    private int totalPages;
}
