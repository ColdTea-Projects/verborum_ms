package de.coldtea.verborum.msdictionary.dictionaries;

import de.coldtea.verborum.msdictionary.dictionaries.domain.DictionaryService;
import de.coldtea.verborum.msdictionary.dictionaries.entity.Dictionary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/dictionary")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryService dictionaryService;

    @PostMapping("/")
    public String createDictionary(@RequestBody Dictionary dictionary){
        Dictionary saved = dictionaryService.createDictionary(dictionary);
        return saved.getDictionaryId();
    }

    @GetMapping("/")
    public List<Dictionary> getAllDictionaries(){
        return dictionaryService.getDictionaries();
    }
}
