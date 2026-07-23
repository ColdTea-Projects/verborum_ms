package de.coldtea.verborum.msdictionary.tag.repository;

import de.coldtea.verborum.msdictionary.tag.entity.DictionaryTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DictionaryTagRepository extends JpaRepository<DictionaryTag, String> {

    List<DictionaryTag> findByDictionaryId(String dictionaryId);
    List<DictionaryTag> findByDictionaryIdIn(List<String> dictionaryIds);
    Optional<DictionaryTag> findByDictionaryIdAndTag(String dictionaryId, String tag);
    void deleteByDictionaryIdAndTag(String dictionaryId, String tag);

}
