package de.coldtea.verborum.msdictionary.dictionary.repository;

import de.coldtea.verborum.msdictionary.dictionary.entity.Dictionary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DictionaryRepository extends JpaRepository<Dictionary, String> {
    List<Dictionary> findByUserId(String userId);

    List<Dictionary> findByFromLang(String fromLang);

    List<Dictionary> findByToLang(String toLang);
}
