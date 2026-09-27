package de.coldtea.verborum.msmarketplace.common.mapper;

import de.coldtea.verborum.msmarketplace.dictionarystats.dto.DictionaryListingResponseDTO;
import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DictionaryStatsMapper {

    // The entity's userId (fk_user_id) is published as publisherId: on a public listing it names the
    // publisher, not "the user" — and the name keeps it from being mistaken for a caller id
    @Mapping(source = "userId", target = "publisherId")
    DictionaryListingResponseDTO toDictionaryListingResponseDTO(DictionaryStats dictionaryStats);
}
