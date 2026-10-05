package de.coldtea.verborum.msdictionary.word.dto;

import de.coldtea.verborum.msdictionary.common.utils.ValidUUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

import static de.coldtea.verborum.msdictionary.common.constants.DTOMessageConstants.*;

@Data
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WordBundleRequestDTO {

    @NotBlank(message = DICTIONARY_DICTIONARY_ID)
    @ValidUUID
    private String dictionaryId;

    @Valid
    @NotEmpty(message = WORD_WORD_LIST)
    @Size(max = WORDS_PER_BUNDLE_MAX, message = TOO_MANY_WORDS_IN_BUNDLE)
    private List<WordRequestDTO> words;
}
