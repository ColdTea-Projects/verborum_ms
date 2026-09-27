package de.coldtea.verborum.msmarketplace.dictionarystats.repository;

import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import org.springframework.data.jpa.repository.JpaRepository;

// Query methods arrive with their callers: the listeners at P4-03..P4-05, browse and filter at P4-06
public interface DictionaryStatsRepository extends JpaRepository<DictionaryStats, String> {
}
