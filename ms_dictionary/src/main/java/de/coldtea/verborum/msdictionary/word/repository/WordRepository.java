package de.coldtea.verborum.msdictionary.word.repository;

import de.coldtea.verborum.msdictionary.word.repository.entity.Word;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface WordRepository extends JpaRepository<Word, String> {

    void deleteByDictionaryIdIn(Collection<String> dictionaryId);

    List<Word> findByDictionaryIdIn(Collection<String> dictionaryId);
}
