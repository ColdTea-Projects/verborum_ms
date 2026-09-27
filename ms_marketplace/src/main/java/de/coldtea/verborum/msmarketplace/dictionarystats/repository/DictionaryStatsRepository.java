package de.coldtea.verborum.msmarketplace.dictionarystats.repository;

import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Browse and filter queries arrive with their caller at P4-06. Reconciliation needs nothing beyond
// the inherited findAll / saveAllAndFlush / deleteAllInBatch
public interface DictionaryStatsRepository extends JpaRepository<DictionaryStats, String> {

    // The user.deleted cascade (P4-05), backed by idx_dictionary_stats_user
    List<DictionaryStats> findByUserId(String userId);
}
