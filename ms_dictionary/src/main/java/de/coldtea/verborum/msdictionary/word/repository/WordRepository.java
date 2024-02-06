package de.coldtea.verborum.msdictionary.word.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WordRepository extends JpaRepository<Word, String> {

    @Query("delete from Word w where w.dictionaryId IN :dictionary_ids")
    void deleteByDictionaryIds(@Param("dictionary_ids")  List<String> dictionaryIds);

    @Query("select w from Word w where w.dictionaryId IN :dictionary_ids")
    List<Word> getByDictionaryIds(@Param("dictionary_ids") List<String> dictionaryIds);
}
