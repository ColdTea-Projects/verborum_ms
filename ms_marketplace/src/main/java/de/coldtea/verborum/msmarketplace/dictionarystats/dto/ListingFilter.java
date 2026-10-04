package de.coldtea.verborum.msmarketplace.dictionarystats.dto;

import java.util.List;

/**
 * The optional browse filters (P4-11, P4-12), as the request sent them — validated, not yet
 * normalised. An absent or empty filter does not restrict the result. The publisher name (P4-13)
 * joins here.
 *
 * @param pairs language pairs such as `EN-TR`, in any case and either direction
 * @param tags  tags in any case, with stray whitespace allowed; a listing matches if it has any of them
 */
public record ListingFilter(List<String> pairs, List<String> tags) {

    public static final ListingFilter NONE = new ListingFilter(null, null);
}
