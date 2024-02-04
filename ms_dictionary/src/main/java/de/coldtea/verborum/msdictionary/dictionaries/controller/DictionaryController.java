package de.coldtea.verborum.msdictionary.dictionaries.controller;

import de.coldtea.verborum.msdictionary.dictionaries.services.DictionaryService;
import de.coldtea.verborum.msdictionary.dictionaries.repository.Dictionary;
import de.coldtea.verborum.msdictionary.words.repository.Word;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
