package de.coldtea.verborum.msdictionary.dictionary.repository;

import de.coldtea.verborum.msdictionary.dictionary.entity.Dictionary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DictionaryRepository extends JpaRepository<Dictionary, String> {
    List<Dictionary> findByUserId(String userId);

    // Real bulk DELETE ... WHERE dictionary_id IN (...). JpaRepository.deleteAllById() looks
    // equivalent but is not: it loads each entity and issues one DELETE per id, so deleting a user
    // with hundreds of dictionaries meant hundreds of round-trips holding locks inside one
    // transaction. Mirrors WordRepository.deleteByDictionaryIdIn.
    void deleteByDictionaryIdIn(Collection<String> dictionaryIds);

    List<Dictionary> findByFromLang(String fromLang);

    List<Dictionary> findByToLang(String toLang);

    // The dictionary.snapshot query (P4-03), backed by idx_dictionaries_is_public
    List<Dictionary> findByIsPublicTrue();

    // The sharing rule (P4-16): the owner's other dictionaries, all and public ones, excluding the one
    // being changed — so the check is on the result of the change
    long countByUserIdAndDictionaryIdNot(String userId, String dictionaryId);

    long countByUserIdAndIsPublicTrueAndDictionaryIdNot(String userId, String dictionaryId);
}
