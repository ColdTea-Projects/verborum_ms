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
}
