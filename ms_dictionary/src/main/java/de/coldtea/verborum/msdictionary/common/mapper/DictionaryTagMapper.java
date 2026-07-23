package de.coldtea.verborum.msdictionary.common.mapper;

import de.coldtea.verborum.msdictionary.tag.dto.DictionaryTagResponseDTO;
import de.coldtea.verborum.msdictionary.tag.entity.DictionaryTag;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DictionaryTagMapper {

    DictionaryTagResponseDTO toDictionaryTagResponseDTO(DictionaryTag dictionaryTag);
}
