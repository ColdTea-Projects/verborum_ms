package de.coldtea.verborum.msmarketplace.dictionarystats.repository;

import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

/**
 * The browse filters (P4-11), one condition each, combined by the service for whichever filters a
 * request carries. Attribute names are the entity's, not the columns'.
 */
public class DictionaryStatsSpecifications {

    private DictionaryStatsSpecifications() {
    }

    /**
     * Every browse read starts here — a hidden row is a private or deleted dictionary, and returning
     * one would leak it (P4-04).
     * <p>
     * `isTrue` renders the literal `is_listed = true`, never a bound parameter. The browse indexes are
     * partial (`WHERE is_listed`), and Postgres can only use one when it can prove the query's
     * condition implies the index's — which it cannot do for a parameter in a cached generic plan.
     */
    public static Specification<DictionaryStats> isListed() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isTrue(root.get("isListed"));
    }

    /** Canonical pairs only (`LanguagePairUtils`), which is what `lang_pair` stores. */
    public static Specification<DictionaryStats> hasLangPairIn(Collection<String> langPairs) {
        return (root, query, criteriaBuilder) -> root.get("langPair").in(langPairs);
    }

    public static Specification<DictionaryStats> isPublishedBy(String publisherId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("userId"), publisherId);
    }
}
