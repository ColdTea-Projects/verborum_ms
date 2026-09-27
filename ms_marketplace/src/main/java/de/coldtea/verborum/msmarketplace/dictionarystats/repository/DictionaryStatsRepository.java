package de.coldtea.verborum.msmarketplace.dictionarystats.repository;

import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Reconciliation needs nothing beyond the inherited findAll / saveAllAndFlush / deleteAllInBatch
public interface DictionaryStatsRepository extends JpaRepository<DictionaryStats, String> {

    // The user.deleted cascade (P4-05), backed by idx_dictionary_stats_user
    List<DictionaryStats> findByUserId(String userId);

    // Browse (P4-06). Every one filters IsListedTrue — a hidden row is a private or deleted
    // dictionary, and returning one would leak it. Order comes from the Pageable's Sort
    Page<DictionaryStats> findByIsListedTrue(Pageable pageable);

    Page<DictionaryStats> findByIsListedTrueAndFromLangAndToLang(String fromLang, String toLang, Pageable pageable);

    Page<DictionaryStats> findByIsListedTrueAndUserId(String userId, Pageable pageable);
}
