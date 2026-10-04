package de.coldtea.verborum.msmarketplace.publisher.service.impl;

import de.coldtea.verborum.msmarketplace.common.event.UserProfileUpdatedEvent;
import de.coldtea.verborum.msmarketplace.publisher.entity.Publisher;
import de.coldtea.verborum.msmarketplace.publisher.repository.PublisherRepository;
import de.coldtea.verborum.msmarketplace.publisher.service.PublisherService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PublisherServiceImpl implements PublisherService {

    private final PublisherRepository publisherRepository;

    /**
     * A late event for a user who has since been deleted recreates their row. Harmless: their listings
     * are gone (user.deleted removes them) and a Keycloak subject is never reused, so the row names
     * nothing anyone can see.
     */
    @Transactional
    @Override
    public void updateDisplayName(UserProfileUpdatedEvent event) {
        // A missing updatedAt falls back to the event timestamp, as for the dictionary events
        OffsetDateTime sourceUpdatedAt = Objects.requireNonNullElse(event.getUpdatedAt(), event.getEventTimestamp());
        String displayName = normalizeDisplayName(event.getDisplayName());

        publisherRepository.findById(event.getKeycloakId())
                .ifPresentOrElse(
                        publisher -> {
                            // Equal counts as stale, so a redelivery is a no-op (rule 4)
                            if (sourceUpdatedAt.isAfter(publisher.getSourceUpdatedAt())) {
                                publisher.setDisplayName(displayName);
                                // Null = a pre-P4-14 event that does not know the agreement: keep ours
                                if (event.getMarketplaceAgreementAccepted() != null) {
                                    publisher.setMarketplaceAgreementAccepted(event.getMarketplaceAgreementAccepted());
                                }
                                publisher.setSourceUpdatedAt(sourceUpdatedAt);
                                publisherRepository.saveAndFlush(publisher);
                            }
                        },
                        () -> publisherRepository.saveAndFlush(Publisher.builder()
                                .keycloakId(event.getKeycloakId())
                                .displayName(displayName)
                                // Unknown counts as not accepted: the marketplace is opt-in
                                .marketplaceAgreementAccepted(Boolean.TRUE.equals(event.getMarketplaceAgreementAccepted()))
                                .sourceUpdatedAt(sourceUpdatedAt)
                                .build()));
    }

    /**
     * Trimmed, and blank becomes null: browse hides publishers without a name, and "   " is not a name.
     * ms_user stores the name as sent, so this is the one place that decides.
     */
    private static String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        return displayName.trim();
    }
}
