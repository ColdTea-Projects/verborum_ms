package de.coldtea.verborum.msmarketplace.dictionarystats.repository;

import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import de.coldtea.verborum.msmarketplace.common.utils.LikePatternUtils;
import de.coldtea.verborum.msmarketplace.publisher.entity.Publisher;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

/**
 * The browse filters (P4-11..P4-13), one condition each, combined by the service for whichever filters a
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

    /**
     * The listing's publisher is on the marketplace: has a display name (P4-13) and has accepted the
     * marketplace terms (P4-14) — and, given a pattern, has a name that matches it. Every browse read
     * applies it: the marketplace is opt-in, and withdrawing hides a user's listings.
     * <p>
     * `EXISTS (SELECT 1 FROM publishers p WHERE p.keycloak_id = fk_user_id
     * AND p.display_name IS NOT NULL AND p.marketplace_agreement_accepted
     * [AND lower(p.display_name) LIKE :pattern ESCAPE '\'])`.
     * A subquery rather than a join: there is no association to join on, and EXISTS cannot duplicate a
     * listing. The LIKE is on
     * `lower(display_name)` because that is the expression the trigram index covers.
     *
     * @param namePattern a ready pattern from {@link LikePatternUtils#toContainsPattern}, or null for "any name"
     */
    public static Specification<DictionaryStats> hasActivePublisher(String namePattern) {
        return (root, query, criteriaBuilder) -> {
            Subquery<String> publisher = query.subquery(String.class);
            Root<Publisher> publisherRoot = publisher.from(Publisher.class);

            Predicate named = criteriaBuilder.and(
                    criteriaBuilder.equal(publisherRoot.get("keycloakId"), root.get("userId")),
                    criteriaBuilder.isNotNull(publisherRoot.get("displayName")),
                    // A literal, like isListed(): no bound parameter in the plan
                    criteriaBuilder.isTrue(publisherRoot.get("marketplaceAgreementAccepted")));
            if (namePattern != null) {
                named = criteriaBuilder.and(named, criteriaBuilder.like(
                        criteriaBuilder.lower(publisherRoot.get("displayName")), namePattern, LikePatternUtils.ESCAPE));
            }

            return criteriaBuilder.exists(publisher.select(publisherRoot.get("keycloakId")).where(named));
        };
    }

    public static Specification<DictionaryStats> isPublishedBy(String publisherId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("userId"), publisherId);
    }
}
