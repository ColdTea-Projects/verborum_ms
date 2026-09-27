package de.coldtea.verborum.msmarketplace.dictionaryimport.repository;

import de.coldtea.verborum.msmarketplace.dictionaryimport.entity.DictionaryImport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DictionaryImportRepository extends JpaRepository<DictionaryImport, String> {

    Optional<DictionaryImport> findByDictionaryIdAndUserId(String dictionaryId, String userId);

    // The user.deleted cascade: a deleted importer's records, backed by idx_dictionary_imports_user
    List<DictionaryImport> findByUserId(String userId);
}
