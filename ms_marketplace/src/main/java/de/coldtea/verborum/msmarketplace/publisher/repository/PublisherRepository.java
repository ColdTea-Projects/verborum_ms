package de.coldtea.verborum.msmarketplace.publisher.repository;

import de.coldtea.verborum.msmarketplace.publisher.entity.Publisher;
import org.springframework.data.jpa.repository.JpaRepository;

// Names for a page of listings come from the inherited findAllById — one query per page
public interface PublisherRepository extends JpaRepository<Publisher, String> {
}
