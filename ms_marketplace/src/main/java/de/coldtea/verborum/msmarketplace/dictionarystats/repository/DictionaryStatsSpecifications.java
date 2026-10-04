package de.coldtea.verborum.msmarketplace.dictionarystats.repository;

import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

/**
 * The browse filters (P4-11, P4-12), one condition each, combined by the service for whichever filters a
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

    /**
     * Any of the given tags (P4-12): Postgres `tags && ARRAY[...]`, served by the GIN index. Tags must
     * already be normalised the way they are stored.
     * <p>
     * Hibernate's own builder, not JPA's: standard Criteria has no array operators.
     */
    public static Specification<DictionaryStats> hasAnyTag(String[] tags) {
        return (root, query, criteriaBuilder) ->
                ((HibernateCriteriaBuilder) criteriaBuilder).arrayOverlaps(root.get("tags"), tags);
    }

    public static Specification<DictionaryStats> isPublishedBy(String publisherId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("userId"), publisherId);
    }
}
