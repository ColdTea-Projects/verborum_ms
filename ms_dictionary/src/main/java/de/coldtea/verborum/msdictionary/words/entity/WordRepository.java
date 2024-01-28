package de.coldtea.verborum.msdictionary.words.entity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WordRepository extends JpaRepository<Word, String> {}
