package de.coldtea.verborum.msdictionary.dictionary.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DictionaryRepository extends JpaRepository<Dictionary, String> {
    @Query("select d from Dictionary d where d.userId = :user_id")
    List<Dictionary> getByUserId(@Param("user_id") String userId);

    @Query("select d from Dictionary d where d.fromLang = :language")
    List<Dictionary> getByLanguageFrom(@Param("language") String language);

    @Query("select d from Dictionary d where d.toLang = :language")
    List<Dictionary> getByLanguageTo(@Param("language") String language);
}
