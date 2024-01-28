package de.coldtea.verborum.msdictionary.dictionaries.entity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DictionaryRepository extends JpaRepository<Dictionary, String> {}
