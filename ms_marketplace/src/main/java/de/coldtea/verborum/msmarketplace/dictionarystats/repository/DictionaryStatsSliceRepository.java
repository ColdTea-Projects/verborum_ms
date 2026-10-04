package de.coldtea.verborum.msmarketplace.dictionarystats.repository;

import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;

/**
 * Browse reads (P4-11): a filtered, sorted page that never runs a count query.
 * <p>
 * A custom fragment because Spring Data JPA 3.2 has no count-free page for a Specification —
 * `findAll(spec, pageable)` always runs `COUNT(*)` for its `Page`, and the fluent `findBy(spec, ...)`
 * cannot skip an offset. Derived `Slice` methods are count-free but cannot take optional filters
 * without one method per combination.
 */
public interface DictionaryStatsSliceRepository {

    Slice<DictionaryStats> findSlice(Specification<DictionaryStats> specification, Pageable pageable);
}
