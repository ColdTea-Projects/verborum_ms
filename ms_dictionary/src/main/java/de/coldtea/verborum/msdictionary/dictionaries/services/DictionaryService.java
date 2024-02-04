package de.coldtea.verborum.msdictionary.dictionaries.services;

import de.coldtea.verborum.msdictionary.dictionaries.repository.Dictionary;
import de.coldtea.verborum.msdictionary.dictionaries.repository.DictionaryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DictionaryService {

    private DictionaryRepository dictionaryRepository;

    public DictionaryService(DictionaryRepository dictionaryRepository) {
        this.dictionaryRepository = dictionaryRepository;
    }

    @Transactional
    public Dictionary createDictionary(Dictionary dictionary){
        return dictionaryRepository.save(dictionary);
    }

    public List<Dictionary> getDictionaries(){
        return dictionaryRepository.findAll();
    }
}
