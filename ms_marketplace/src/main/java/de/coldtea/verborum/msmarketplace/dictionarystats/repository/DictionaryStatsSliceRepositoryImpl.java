package de.coldtea.verborum.msmarketplace.dictionarystats.repository;

import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.springframework.data.jpa.repository.query.QueryUtils.toOrders;

/**
 * Picked up by name (`DictionaryStatsSliceRepository` + `Impl`) and merged into
 * {@link DictionaryStatsRepository}.
 * <p>
 * Asks for one row more than the page size: if it comes back there is a next page, and the extra row
 * is dropped. That is the whole cost of `hasNext` — no second query.
 */
@RequiredArgsConstructor
public class DictionaryStatsSliceRepositoryImpl implements DictionaryStatsSliceRepository {

    private final EntityManager entityManager;

    @Override
    public Slice<DictionaryStats> findSlice(Specification<DictionaryStats> specification, Pageable pageable) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<DictionaryStats> query = criteriaBuilder.createQuery(DictionaryStats.class);
        Root<DictionaryStats> root = query.from(DictionaryStats.class);

        Predicate predicate = specification.toPredicate(root, query, criteriaBuilder);
        if (predicate != null) {
            query.where(predicate);
        }
        query.orderBy(toOrders(pageable.getSort(), root, criteriaBuilder));

        int size = pageable.getPageSize();
        List<DictionaryStats> rows = entityManager.createQuery(query)
                .setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(size + 1)
                .getResultList();

        boolean hasNext = rows.size() > size;
        return new SliceImpl<>(hasNext ? rows.subList(0, size) : rows, pageable, hasNext);
    }
}
