package de.coldtea.verborum.msdictionary.common.mapper;

import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryRequestDTO;
import de.coldtea.verborum.msdictionary.dictionary.dto.DictionaryResponseDTO;
import de.coldtea.verborum.msdictionary.dictionary.repository.entity.Dictionary;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DictionaryMapper {

    DictionaryResponseDTO toDictionaryResponseDTO(Dictionary dictionary);

    Dictionary toDictionary(DictionaryRequestDTO dictionaryRequestDTO);
}
