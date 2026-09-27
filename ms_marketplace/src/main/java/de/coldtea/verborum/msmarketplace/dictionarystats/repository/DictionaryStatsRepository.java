package de.coldtea.verborum.msmarketplace.dictionarystats.repository;

import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import org.springframework.data.jpa.repository.JpaRepository;

// Browse and filter queries arrive with their caller at P4-06. Reconciliation needs nothing beyond
// the inherited findAll / saveAllAndFlush / deleteAllInBatch
public interface DictionaryStatsRepository extends JpaRepository<DictionaryStats, String> {
}
