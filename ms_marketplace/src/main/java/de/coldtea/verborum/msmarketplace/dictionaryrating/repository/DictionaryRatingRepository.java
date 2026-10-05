package de.coldtea.verborum.msmarketplace.dictionaryrating.repository;

import de.coldtea.verborum.msmarketplace.dictionaryrating.entity.DictionaryRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DictionaryRatingRepository extends JpaRepository<DictionaryRating, String> {

    // Backed by uq_dictionary_ratings_dictionary_user
    Optional<DictionaryRating> findByDictionaryIdAndUserId(String dictionaryId, String userId);

    // The user.deleted cleanup of a deleted rater's ratings, backed by idx_dictionary_ratings_user
    List<DictionaryRating> findByUserId(String userId);
}
