package de.coldtea.verborum.msuser.vault.repository;

import de.coldtea.verborum.msuser.vault.entity.VaultEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VaultEntryRepository extends JpaRepository<VaultEntry, String> {

    List<VaultEntry> findByUserId(String userId);
    Optional<VaultEntry> findByUserIdAndDictionaryId(String userId, String dictionaryId);
    void deleteByUserIdAndDictionaryId(String userId, String dictionaryId);

}
