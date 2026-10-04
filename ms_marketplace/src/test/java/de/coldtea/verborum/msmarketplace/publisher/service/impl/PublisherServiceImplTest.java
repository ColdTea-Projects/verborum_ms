package de.coldtea.verborum.msmarketplace.publisher.service.impl;

import de.coldtea.verborum.msmarketplace.common.event.UserProfileUpdatedEvent;
import de.coldtea.verborum.msmarketplace.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msmarketplace.publisher.entity.Publisher;
import de.coldtea.verborum.msmarketplace.publisher.repository.PublisherRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PublisherServiceImplTest {

    private static final String KEYCLOAK_ID = "kc-1";

    private static final OffsetDateTime T1 = OffsetDateTime.of(2026, 10, 4, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T2 = T1.plusMinutes(5);

    @Mock
    private PublisherRepository publisherRepository;

    @InjectMocks
    private PublisherServiceImpl publisherService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void updateDisplayName_UnknownPublisher_CreatesIt() {
        // Arrange
        when(publisherRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.empty());

        // Act
        publisherService.updateDisplayName(event("Anna Bauer", T1));

        // Assert
        Publisher saved = capturedSave();
        assertEquals(KEYCLOAK_ID, saved.getKeycloakId());
        assertEquals("Anna Bauer", saved.getDisplayName());
        assertEquals(T1, saved.getSourceUpdatedAt());
    }

    @Test
    void updateDisplayName_NewerEvent_Renames() {
        // Arrange
        Publisher held = publisher("Anna", T1);
        when(publisherRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.of(held));

        // Act
        publisherService.updateDisplayName(event("Anna Bauer", T2));

        // Assert
        assertEquals("Anna Bauer", capturedSave().getDisplayName());
        assertEquals(T2, held.getSourceUpdatedAt());
    }

    @Test
    void updateDisplayName_OlderEvent_IsDropped() {
        // Arrange — two renames delivered in reverse must not leave the older name (rule 4)
        Publisher held = publisher("Anna Bauer", T2);
        when(publisherRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.of(held));

        // Act
        publisherService.updateDisplayName(event("Anna", T1));

        // Assert
        verify(publisherRepository, never()).saveAndFlush(any());
        assertEquals("Anna Bauer", held.getDisplayName());
    }

    @Test
    void updateDisplayName_RedeliveredEvent_ChangesNothing() {
        // Arrange — equal counts as stale
        when(publisherRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.of(publisher("Anna", T1)));

        // Act
        publisherService.updateDisplayName(event("Anna", T1));

        // Assert
        verify(publisherRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateDisplayName_Cleared_KeepsTheRowWithNullName() {
        // Arrange — the row stays as a stale-event guard; a null name hides the listings
        when(publisherRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.of(publisher("Anna", T1)));

        // Act
        publisherService.updateDisplayName(event(null, T2));

        // Assert
        Publisher saved = capturedSave();
        assertNull(saved.getDisplayName());
        assertEquals(T2, saved.getSourceUpdatedAt());
    }

    @Test
    void updateDisplayName_BlankName_IsStoredAsNull() {
        // Arrange — "   " is not a name; ms_user stores whatever it is sent
        when(publisherRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.empty());

        // Act
        publisherService.updateDisplayName(event("   ", T1));

        // Assert
        assertNull(capturedSave().getDisplayName());
    }

    @Test
    void updateDisplayName_SurroundingWhitespace_IsTrimmed() {
        // Arrange
        when(publisherRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.empty());

        // Act
        publisherService.updateDisplayName(event("  Anna Bauer ", T1));

        // Assert
        assertEquals("Anna Bauer", capturedSave().getDisplayName());
    }

    @Test
    void updateDisplayName_MissingUpdatedAt_FallsBackToEventTimestamp() {
        // Arrange
        when(publisherRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.empty());
        UserProfileUpdatedEvent event = event("Anna", null);
        event.setEventTimestamp(T2);

        // Act
        publisherService.updateDisplayName(event);

        // Assert
        assertEquals(T2, capturedSave().getSourceUpdatedAt());
    }

    // ---- marketplace agreement (P4-14) ----

    @Test
    void updateDisplayName_NewPublisherWhoAccepted_IsActive() {
        // Arrange
        when(publisherRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.empty());
        UserProfileUpdatedEvent event = event("Anna", T1);
        event.setMarketplaceAgreementAccepted(true);

        // Act
        publisherService.updateDisplayName(event);

        // Assert
        assertTrue(capturedSave().getMarketplaceAgreementAccepted());
    }

    @Test
    void updateDisplayName_NewPublisherWithoutTheFlag_IsNotAccepted() {
        // Arrange — a pre-P4-14 event; the marketplace is opt-in, so unknown means not accepted
        when(publisherRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.empty());

        // Act
        publisherService.updateDisplayName(event("Anna", T1));

        // Assert
        assertFalse(capturedSave().getMarketplaceAgreementAccepted());
    }

    @Test
    void updateDisplayName_NewerWithdrawal_HidesThePublisher() {
        // Arrange
        Publisher held = publisher("Anna", T1);
        held.setMarketplaceAgreementAccepted(true);
        when(publisherRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.of(held));
        UserProfileUpdatedEvent event = event("Anna", T2);
        event.setMarketplaceAgreementAccepted(false);

        // Act
        publisherService.updateDisplayName(event);

        // Assert — the name stays; only the flag goes, and nothing is deleted
        Publisher saved = capturedSave();
        assertFalse(saved.getMarketplaceAgreementAccepted());
        assertEquals("Anna", saved.getDisplayName());
    }

    @Test
    void updateDisplayName_NewerEventWithoutTheFlag_KeepsTheHeldAgreement() {
        // Arrange — a pre-P4-14 message (e.g. a DLQ replay) must not withdraw anyone
        Publisher held = publisher("Anna", T1);
        held.setMarketplaceAgreementAccepted(true);
        when(publisherRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.of(held));

        // Act
        publisherService.updateDisplayName(event("Anna Bauer", T2));

        // Assert
        Publisher saved = capturedSave();
        assertTrue(saved.getMarketplaceAgreementAccepted());
        assertEquals("Anna Bauer", saved.getDisplayName());
    }

    @Test
    void updateDisplayName_OlderWithdrawal_IsDropped() {
        // Arrange — a withdrawal overtaken by a later re-acceptance must not hide the listings (rule 4)
        Publisher held = publisher("Anna", T2);
        held.setMarketplaceAgreementAccepted(true);
        when(publisherRepository.findById(KEYCLOAK_ID)).thenReturn(Optional.of(held));
        UserProfileUpdatedEvent event = event("Anna", T1);
        event.setMarketplaceAgreementAccepted(false);

        // Act
        publisherService.updateDisplayName(event);

        // Assert
        verify(publisherRepository, never()).saveAndFlush(any());
        assertTrue(held.getMarketplaceAgreementAccepted());
    }

    // ---- membership: the Forum gate ----

    @Test
    void requireMember_Member_Passes() {
        // Arrange
        when(publisherRepository.existsByKeycloakIdAndDisplayNameIsNotNullAndMarketplaceAgreementAcceptedTrue(KEYCLOAK_ID))
                .thenReturn(true);

        // Act & Assert — no exception
        publisherService.requireMember(KEYCLOAK_ID);
        assertTrue(publisherService.isMember(KEYCLOAK_ID));
    }

    @Test
    void requireMember_NotAMember_IsForbidden() {
        // Arrange — no row, no name or not accepted all look the same to the query
        when(publisherRepository.existsByKeycloakIdAndDisplayNameIsNotNullAndMarketplaceAgreementAcceptedTrue(KEYCLOAK_ID))
                .thenReturn(false);

        // Act & Assert
        assertThrows(ForbiddenOperationException.class, () -> publisherService.requireMember(KEYCLOAK_ID));
    }

    private Publisher capturedSave() {
        ArgumentCaptor<Publisher> captor = ArgumentCaptor.forClass(Publisher.class);
        verify(publisherRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static UserProfileUpdatedEvent event(String displayName, OffsetDateTime updatedAt) {
        return UserProfileUpdatedEvent.builder()
                .keycloakId(KEYCLOAK_ID)
                .displayName(displayName)
                .updatedAt(updatedAt)
                .eventTimestamp(T2.plusMinutes(1))
                .build();
    }

    private static Publisher publisher(String displayName, OffsetDateTime sourceUpdatedAt) {
        return Publisher.builder()
                .keycloakId(KEYCLOAK_ID)
                .displayName(displayName)
                .sourceUpdatedAt(sourceUpdatedAt)
                .build();
    }
}
