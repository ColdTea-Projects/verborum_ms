package de.coldtea.verborum.msdictionary.common.mapper;

import de.coldtea.verborum.msdictionary.word.dto.WordRequestDTO;
import de.coldtea.verborum.msdictionary.word.dto.WordResponseDTO;
import de.coldtea.verborum.msdictionary.word.repository.entity.Word;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")

public interface WordMapper {
    Word toWord(WordRequestDTO wordRequestDTO);
    WordResponseDTO toWordResponseDTO(Word word);
}
