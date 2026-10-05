package de.coldtea.verborum.msdictionary.word.repository;

import de.coldtea.verborum.msdictionary.word.entity.Word;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface WordRepository extends JpaRepository<Word, String> {

    void deleteByDictionaryIdIn(Collection<String> dictionaryId);
    void deleteWordsByDictionaryId(String dictionaryId);
    List<Word> findByDictionaryIdIn(List<String> dictionaryId);

    // SEC-07: the per-dictionary word quota
    long countByDictionaryId(String dictionaryId);

}
