package de.coldtea.verborum.msmarketplace.common.mapper;

import de.coldtea.verborum.msmarketplace.dictionarystats.dto.DictionaryListingResponseDTO;
import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import de.coldtea.verborum.msmarketplace.common.utils.RatingScore;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = RatingScore.class)
public interface DictionaryStatsMapper {

    // The entity's userId (fk_user_id) is published as publisherId: on a public listing it names the
    // publisher, not "the user" — and the name keeps it from being mistaken for a caller id
    @Mapping(source = "userId", target = "publisherId")
    // Not on the listing row: looked up from publishers, one query per page (P4-13)
    @Mapping(target = "publisherName", ignore = true)
    // The displayed average (P4-21); the ranking score stays internal
    @Mapping(target = "ratingAverage", expression = "java(RatingScore.average(dictionaryStats.getRatingSum(), dictionaryStats.getRatingCount()))")
    DictionaryListingResponseDTO toDictionaryListingResponseDTO(DictionaryStats dictionaryStats);
}
