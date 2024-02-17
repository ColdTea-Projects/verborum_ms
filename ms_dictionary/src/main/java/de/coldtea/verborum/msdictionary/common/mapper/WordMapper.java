package de.coldtea.verborum.msdictionary.common.mapper;

import de.coldtea.verborum.msdictionary.word.dto.WordRequestDTO;
import de.coldtea.verborum.msdictionary.word.dto.WordResponseDTO;
import de.coldtea.verborum.msdictionary.word.entity.Word;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")

public interface WordMapper {

    @Mapping(source = "dictionaryId", target = "dictionaryId")
    Word toWord(String dictionaryId, WordRequestDTO wordRequestDTO);

    WordResponseDTO toWordResponseDTO(Word word);
}
