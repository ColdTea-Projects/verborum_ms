package de.coldtea.verborum.msmarketplace.dictionarystats.dto;

import java.util.List;

/**
 * The optional browse filters (P4-11), as the request sent them — validated, not yet normalised. An
 * absent or empty filter does not restrict the result. Tags (P4-12) and the publisher name (P4-13) join
 * here.
 *
 * @param pairs language pairs such as `EN-TR`, in any case and either direction
 */
public record ListingFilter(List<String> pairs) {

    public static final ListingFilter NONE = new ListingFilter(null);
}
