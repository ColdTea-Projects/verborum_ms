package de.coldtea.verborum.msdictionary.word.dto;

import de.coldtea.verborum.msdictionary.common.utils.ValidUUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;
import java.util.stream.Stream;

import static de.coldtea.verborum.msdictionary.common.constants.DTOMessageConstants.*;

@Data
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WordBundleRequestDTO {

    @NotBlank(message = DICTIONARY_DICTIONARY_ID)
    @ValidUUID(fieldName = DICTIONARY_ID)
    private String dictionaryId;

    @NotEmpty(message = WORD_WORD_LIST)
    private List<WordRequestDTO> words;

    public Stream<WordRequestDTO> getWordStream() {
        return words.stream();
    }
}
