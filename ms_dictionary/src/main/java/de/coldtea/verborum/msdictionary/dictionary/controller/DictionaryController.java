package de.coldtea.verborum.msdictionary.dictionary.controller;

import de.coldtea.verborum.msdictionary.dictionary.service.DictionaryService;
import de.coldtea.verborum.msdictionary.dictionary.repository.entity.Dictionary;
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
