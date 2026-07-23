package de.coldtea.verborum.msdictionary.tag.dto;

import lombok.*;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DictionaryTagResponseDTO {

    private String tagId;

    private String dictionaryId;

    private String tag;

    private OffsetDateTime createdAt;

}
