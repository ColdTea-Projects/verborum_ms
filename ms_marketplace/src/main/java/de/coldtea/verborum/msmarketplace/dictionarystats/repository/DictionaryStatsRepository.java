package de.coldtea.verborum.msmarketplace.dictionarystats.repository;

import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// Reconciliation needs nothing beyond the inherited findAll / saveAllAndFlush / deleteAllInBatch.
// Browse (P4-11) is findSlice, from DictionaryStatsSliceRepository, with DictionaryStatsSpecifications
public interface DictionaryStatsRepository extends JpaRepository<DictionaryStats, String>, DictionaryStatsSliceRepository {

    // The user.deleted cascade (P4-05), backed by idx_dictionary_stats_user
    List<DictionaryStats> findByUserId(String userId);

    /**
     * Import (P4-07). One atomic `UPDATE ... SET import_count = import_count + 1` in the database — not
     * read-modify-write in Java, where two users importing at once would both read 5 and both write 6.
     * JPQL because a derived query cannot express an increment.
     * <p>
     * `clearAutomatically`: the UPDATE bypasses the persistence context, so a `DictionaryStats`
     * loaded earlier in the same transaction still holds the old count — and saving it later would
     * silently write the old count back. Clearing afterwards detaches it, making that impossible.
     */
    @Modifying(clearAutomatically = true)
    @Query("update DictionaryStats d set d.importCount = d.importCount + 1 where d.dictionaryId = :dictionaryId")
    int incrementImportCount(@Param("dictionaryId") String dictionaryId);
}
