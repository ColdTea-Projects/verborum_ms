package de.coldtea.verborum.msdictionary.dictionaries;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DictionaryController {

    @GetMapping("/hello")
    public String sayHello(){
        return "Hello Verborum!";
    }
}
