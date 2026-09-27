package de.coldtea.verborum.msmarketplace.dictionarystats.service.impl;

import de.coldtea.verborum.msmarketplace.common.event.DictionaryDeletedEvent;
import de.coldtea.verborum.msmarketplace.common.event.DictionarySnapshotEntry;
import de.coldtea.verborum.msmarketplace.common.event.DictionarySnapshotEvent;
import de.coldtea.verborum.msmarketplace.common.event.DictionaryUpdatedEvent;
import de.coldtea.verborum.msmarketplace.common.event.DictionaryVisibilityEvent;
import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import de.coldtea.verborum.msmarketplace.dictionarystats.repository.DictionaryStatsRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DictionaryStatsServiceImplTest {

    private static final String DICTIONARY_ID = "dict1";
    private static final String OWNER = "kc-1";

    private static final OffsetDateTime T1 = OffsetDateTime.of(2026, 9, 27, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T2 = T1.plusMinutes(5);
    private static final OffsetDateTime T3 = T1.plusMinutes(10);

    @Mock
    private DictionaryStatsRepository dictionaryStatsRepository;

    @InjectMocks
    private DictionaryStatsServiceImpl dictionaryStatsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ---- publishListing (dictionary.visibility.public) ----

    @Test
    void publishListing_NewDictionary_CreatesListing() {
        // Arrange
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.empty());

        // Act
        dictionaryStatsService.publishListing(publicEvent("Travel", T2));

        // Assert
        DictionaryStats saved = capturedSave();
        assertEquals(DICTIONARY_ID, saved.getDictionaryId());
        assertEquals(OWNER, saved.getUserId());
        assertEquals("Travel", saved.getName());
        assertEquals("EN", saved.getFromLang());
        assertEquals("DE", saved.getToLang());
        assertEquals(0, saved.getImportCount());
        assertTrue(saved.getIsListed());
        assertEquals(T2, saved.getPublishedAt());
        assertEquals(T2, saved.getSourceUpdatedAt());
    }

    @Test
    void publishListing_ExistingListingNewerEvent_UpdatesInPlaceAndKeepsCounters() {
        // Arrange — upsert on dictionaryId, never a second row (rule 3)
        DictionaryStats listing = listing("Old", T1);
        listing.setImportCount(7);
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(listing));

        // Act
        dictionaryStatsService.publishListing(publicEvent("Travel", T2));

        // Assert
        DictionaryStats saved = capturedSave();
        assertEquals("Travel", saved.getName());
        assertEquals(T2, saved.getSourceUpdatedAt());
        assertEquals(7, saved.getImportCount());
        assertEquals(T1, saved.getPublishedAt());
    }

    @Test
    void publishListing_RedeliveredEvent_ChangesNothing() {
        // Arrange — same updatedAt as held: equal counts as stale
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(listing("Travel", T2)));

        // Act
        dictionaryStatsService.publishListing(publicEvent("Travel", T2));

        // Assert
        verify(dictionaryStatsRepository, never()).saveAndFlush(any());
    }

    @Test
    void publishListing_OlderEvent_IsDropped() {
        // Arrange — delivered out of order after a newer update (rule 4)
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(listing("Newer", T3)));

        // Act
        dictionaryStatsService.publishListing(publicEvent("Older", T2));

        // Assert
        verify(dictionaryStatsRepository, never()).saveAndFlush(any());
    }

    @Test
    void publishListing_EventWithoutUpdatedAt_FallsBackToEventTimestamp() {
        // Arrange — update_dt is nullable in ms_dictionary; source_updated_at is NOT NULL here
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.empty());
        DictionaryVisibilityEvent event = publicEvent("Travel", null);
        event.setEventTimestamp(T3);

        // Act
        dictionaryStatsService.publishListing(event);

        // Assert
        assertEquals(T3, capturedSave().getSourceUpdatedAt());
    }

    @Test
    void publishListing_HiddenRowNewerEvent_RelistsWithNewPublishDate() {
        // Arrange — made private at T1, public again at T2
        DictionaryStats hidden = hiddenListing("Travel", T1);
        hidden.setImportCount(4);
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(hidden));

        // Act
        dictionaryStatsService.publishListing(publicEvent("Travel", T2));

        // Assert — counts as newly published; import history survives
        DictionaryStats saved = capturedSave();
        assertTrue(saved.getIsListed());
        assertEquals(T2, saved.getPublishedAt());
        assertEquals(4, saved.getImportCount());
    }

    @Test
    void publishListing_HiddenRowOlderEvent_StaysHidden() {
        // Arrange — the public event from T1 arrives after the private event from T2 (rule 4). This is
        // the case deleting on private could not handle: there would be no row to compare against
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(hiddenListing("Travel", T2)));

        // Act
        dictionaryStatsService.publishListing(publicEvent("Travel", T1));

        // Assert
        verify(dictionaryStatsRepository, never()).saveAndFlush(any());
    }

    // ---- hideListing (dictionary.visibility.private) ----

    @Test
    void hideListing_ListedRowNewerEvent_HidesAndKeepsHistory() {
        // Arrange
        DictionaryStats listing = listing("Travel", T1);
        listing.setImportCount(9);
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(listing));

        // Act
        dictionaryStatsService.hideListing(privateEvent(T2));

        // Assert — hidden, not deleted: the row keeps rejecting older public state
        DictionaryStats saved = capturedSave();
        assertFalse(saved.getIsListed());
        assertEquals(T2, saved.getSourceUpdatedAt());
        assertEquals(9, saved.getImportCount());
        assertEquals(T1, saved.getPublishedAt());
        verify(dictionaryStatsRepository, never()).delete(any());
    }

    @Test
    void hideListing_NoRow_CreatesHiddenRow() {
        // Arrange — the private event overtook the public one
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.empty());

        // Act
        dictionaryStatsService.hideListing(privateEvent(T2));

        // Assert
        DictionaryStats saved = capturedSave();
        assertFalse(saved.getIsListed());
        assertEquals(T2, saved.getSourceUpdatedAt());
        assertEquals(0, saved.getImportCount());
    }

    @Test
    void hideListing_OlderEvent_IsDropped() {
        // Arrange — private at T1 delivered after public again at T2
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(listing("Travel", T2)));

        // Act
        dictionaryStatsService.hideListing(privateEvent(T1));

        // Assert
        verify(dictionaryStatsRepository, never()).saveAndFlush(any());
    }

    @Test
    void hideListing_RedeliveredEvent_ChangesNothing() {
        // Arrange
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(hiddenListing("Travel", T2)));

        // Act
        dictionaryStatsService.hideListing(privateEvent(T2));

        // Assert
        verify(dictionaryStatsRepository, never()).saveAndFlush(any());
    }

    // ---- updateListing (dictionary.updated) ----

    @Test
    void updateListing_HiddenRowNewerEvent_Relists() {
        // Arrange — dictionary.updated is only sent for a public dictionary, so a newer one proves it
        // went public again even if that public event has not arrived
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(hiddenListing("Old", T1)));

        // Act
        dictionaryStatsService.updateListing(updatedEvent("Renamed", "DE", T2));

        // Assert
        DictionaryStats saved = capturedSave();
        assertTrue(saved.getIsListed());
        assertEquals("Renamed", saved.getName());
        assertEquals(T2, saved.getPublishedAt());
    }

    @Test
    void updateListing_NewerEvent_UpdatesListedFields() {
        // Arrange
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(listing("Old", T1)));

        // Act
        dictionaryStatsService.updateListing(updatedEvent("Renamed", "FR", T2));

        // Assert
        DictionaryStats saved = capturedSave();
        assertEquals("Renamed", saved.getName());
        assertEquals("FR", saved.getToLang());
        assertEquals(T2, saved.getSourceUpdatedAt());
        assertEquals(T1, saved.getPublishedAt());
    }

    @Test
    void updateListing_NoListing_DoesNotCreateOne() {
        // Arrange — could be a dictionary already made private or deleted; recreating would re-list it
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.empty());

        // Act
        dictionaryStatsService.updateListing(updatedEvent("Renamed", "DE", T2));

        // Assert
        verify(dictionaryStatsRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateListing_OlderEvent_IsDropped() {
        // Arrange
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(listing("Newer", T3)));

        // Act
        dictionaryStatsService.updateListing(updatedEvent("Older", "DE", T2));

        // Assert
        verify(dictionaryStatsRepository, never()).saveAndFlush(any());
    }

    // ---- hideDeletedListing (dictionary.deleted) ----

    @Test
    void hideDeletedListing_ListedRow_HidesWithDeletionTimeAsOrderingKey() {
        // Arrange
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(listing("Travel", T1)));

        // Act
        dictionaryStatsService.hideDeletedListing(deletedEvent(T2));

        // Assert — hidden, not deleted: T2 now rejects any older public state still in flight
        DictionaryStats saved = capturedSave();
        assertFalse(saved.getIsListed());
        assertEquals(T2, saved.getSourceUpdatedAt());
        verify(dictionaryStatsRepository, never()).delete(any());
    }

    @Test
    void hideDeletedListing_LatePublicEventAfterwards_IsRejected() {
        // Arrange — the row as hideDeletedListing left it, then a public event from before the deletion
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(hiddenListing("Travel", T2)));

        // Act
        dictionaryStatsService.publishListing(publicEvent("Travel", T1));

        // Assert — a deleted dictionary must not come back to the marketplace
        verify(dictionaryStatsRepository, never()).saveAndFlush(any());
    }

    @Test
    void hideDeletedListing_NoRow_DoesNothing() {
        // Arrange — the event lacks name and languages, so no hidden row can be built
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.empty());

        // Act
        dictionaryStatsService.hideDeletedListing(deletedEvent(T2));

        // Assert
        verify(dictionaryStatsRepository, never()).saveAndFlush(any());
    }

    @Test
    void hideDeletedListing_RedeliveredEvent_ChangesNothing() {
        // Arrange
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(hiddenListing("Travel", T2)));

        // Act
        dictionaryStatsService.hideDeletedListing(deletedEvent(T2));

        // Assert
        verify(dictionaryStatsRepository, never()).saveAndFlush(any());
    }

    // ---- deleteListingsByUser (user.deleted) ----

    @Test
    void deleteListingsByUser_DeletesEveryRowOfThatUser() {
        // Arrange — listed and hidden rows alike
        DictionaryStats listed = listing("One", T1);
        DictionaryStats hidden = hiddenListing("Two", T1);
        hidden.setDictionaryId("dict2");
        when(dictionaryStatsRepository.findByUserId(OWNER)).thenReturn(List.of(listed, hidden));

        // Act
        dictionaryStatsService.deleteListingsByUser(OWNER);

        // Assert
        verify(dictionaryStatsRepository).deleteAllInBatch(List.of(listed, hidden));
    }

    @Test
    void deleteListingsByUser_NoRows_IsANoOp() {
        // Arrange — also what a redelivery sees
        when(dictionaryStatsRepository.findByUserId(OWNER)).thenReturn(List.of());

        // Act
        dictionaryStatsService.deleteListingsByUser(OWNER);

        // Assert
        verify(dictionaryStatsRepository, never()).deleteAllInBatch(any());
    }

    // ---- reconcile (dictionary.snapshot) ----

    @Test
    void reconcile_MissingListing_IsCreated() {
        // Arrange — its visibility.public event was lost
        when(dictionaryStatsRepository.findAll()).thenReturn(List.of());

        // Act
        dictionaryStatsService.reconcile(snapshot(T3, entry(DICTIONARY_ID, "Travel", T2)));

        // Assert
        List<DictionaryStats> saved = capturedSaveAll();
        assertEquals(1, saved.size());
        assertEquals("Travel", saved.get(0).getName());
        assertEquals(0, saved.get(0).getImportCount());
        assertTrue(saved.get(0).getIsListed());
        verify(dictionaryStatsRepository, never()).deleteAllInBatch(any());
    }

    @Test
    void reconcile_StaleListing_IsCorrected() {
        // Arrange — its dictionary.updated was lost
        when(dictionaryStatsRepository.findAll()).thenReturn(List.of(listing("Old", T1)));

        // Act
        dictionaryStatsService.reconcile(snapshot(T3, entry(DICTIONARY_ID, "Renamed", T2)));

        // Assert
        List<DictionaryStats> saved = capturedSaveAll();
        assertEquals(1, saved.size());
        assertEquals("Renamed", saved.get(0).getName());
        assertEquals(T2, saved.get(0).getSourceUpdatedAt());
    }

    @Test
    void reconcile_UpToDateListing_IsLeftAlone() {
        // Arrange
        when(dictionaryStatsRepository.findAll()).thenReturn(List.of(listing("Travel", T2)));

        // Act
        dictionaryStatsService.reconcile(snapshot(T3, entry(DICTIONARY_ID, "Travel", T2)));

        // Assert
        assertTrue(capturedSaveAll().isEmpty());
        verify(dictionaryStatsRepository, never()).deleteAllInBatch(any());
    }

    @Test
    void reconcile_ListingNewerThanItsSnapshotEntry_IsLeftAlone() {
        // Arrange — the listing got an update after the snapshot query; the snapshot must not roll it back
        when(dictionaryStatsRepository.findAll()).thenReturn(List.of(listing("Newest", T3)));

        // Act
        dictionaryStatsService.reconcile(snapshot(T2, entry(DICTIONARY_ID, "Older", T1)));

        // Assert
        assertTrue(capturedSaveAll().isEmpty());
    }

    @Test
    void reconcile_MadePrivateAfterSnapshotQuery_StaysHidden() {
        // Arrange — the snapshot was read at T2 (entry updatedAt T1), then the owner made it private at
        // T3, before the snapshot was processed. The gap P4-03 left open: with the listing deleted on
        // private, the snapshot recreated it. The hidden row's T3 now wins over the entry's T1
        when(dictionaryStatsRepository.findAll()).thenReturn(List.of(hiddenListing("Travel", T3)));

        // Act
        dictionaryStatsService.reconcile(snapshot(T2, entry(DICTIONARY_ID, "Travel", T1)));

        // Assert
        assertTrue(capturedSaveAll().isEmpty());
        verify(dictionaryStatsRepository, never()).deleteAllInBatch(any());
    }

    @Test
    void reconcile_HiddenRowPublicAgainInSnapshot_IsRelisted() {
        // Arrange — the public event that followed the private one was lost
        when(dictionaryStatsRepository.findAll()).thenReturn(List.of(hiddenListing("Travel", T1)));

        // Act
        dictionaryStatsService.reconcile(snapshot(T3, entry(DICTIONARY_ID, "Travel", T2)));

        // Assert
        List<DictionaryStats> saved = capturedSaveAll();
        assertEquals(1, saved.size());
        assertTrue(saved.get(0).getIsListed());
        assertEquals(T2, saved.get(0).getPublishedAt());
    }

    @Test
    void reconcile_HiddenRowOlderThanSnapshot_IsCleanedUp() {
        // Arrange — hidden before the snapshot and confirmed not public by it; keeping it would leave
        // one row forever per dictionary ever made private
        DictionaryStats hidden = hiddenListing("Travel", T1);
        when(dictionaryStatsRepository.findAll()).thenReturn(List.of(hidden));

        // Act
        dictionaryStatsService.reconcile(snapshot(T3));

        // Assert
        verify(dictionaryStatsRepository).deleteAllInBatch(List.of(hidden));
    }

    @Test
    void reconcile_ListingNoLongerPublic_IsRemoved() {
        // Arrange — absent from the snapshot, and last changed before it was taken
        DictionaryStats orphan = listing("Gone", T1);
        when(dictionaryStatsRepository.findAll()).thenReturn(List.of(orphan));

        // Act
        dictionaryStatsService.reconcile(snapshot(T3));

        // Assert
        verify(dictionaryStatsRepository).deleteAllInBatch(List.of(orphan));
    }

    @Test
    void reconcile_ListingPublishedAfterSnapshotWasTaken_IsKept() {
        // Arrange — made public a moment after the query, so legitimately absent from the snapshot
        when(dictionaryStatsRepository.findAll()).thenReturn(List.of(listing("Brand new", T3)));

        // Act
        dictionaryStatsService.reconcile(snapshot(T2));

        // Assert
        verify(dictionaryStatsRepository, never()).deleteAllInBatch(any());
    }

    @Test
    void reconcile_EmptySnapshot_RemovesEveryOlderListing() {
        // Arrange — "nothing is public" is a real statement, not a missing payload
        DictionaryStats first = listing("One", T1);
        DictionaryStats second = listing("Two", T1);
        second.setDictionaryId("dict2");
        when(dictionaryStatsRepository.findAll()).thenReturn(List.of(first, second));

        // Act
        dictionaryStatsService.reconcile(DictionarySnapshotEvent.builder().takenAt(T3).dictionaries(null).build());

        // Assert
        ArgumentCaptor<List<DictionaryStats>> captor = ArgumentCaptor.captor();
        verify(dictionaryStatsRepository).deleteAllInBatch(captor.capture());
        assertEquals(2, captor.getValue().size());
    }

    // ---- helpers ----

    private DictionaryStats capturedSave() {
        ArgumentCaptor<DictionaryStats> captor = ArgumentCaptor.forClass(DictionaryStats.class);
        verify(dictionaryStatsRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private List<DictionaryStats> capturedSaveAll() {
        ArgumentCaptor<List<DictionaryStats>> captor = ArgumentCaptor.captor();
        verify(dictionaryStatsRepository).saveAllAndFlush(captor.capture());
        return captor.getValue();
    }

    private static DictionaryStats listing(String name, OffsetDateTime sourceUpdatedAt) {
        return DictionaryStats.builder()
                .dictionaryId(DICTIONARY_ID)
                .userId(OWNER)
                .name(name)
                .fromLang("EN")
                .toLang("DE")
                .isListed(true)
                .importCount(0)
                .publishedAt(T1)
                .sourceUpdatedAt(sourceUpdatedAt)
                .build();
    }

    private static DictionaryStats hiddenListing(String name, OffsetDateTime sourceUpdatedAt) {
        DictionaryStats listing = listing(name, sourceUpdatedAt);
        listing.setIsListed(false);
        return listing;
    }

    private static DictionaryDeletedEvent deletedEvent(OffsetDateTime eventTimestamp) {
        return DictionaryDeletedEvent.builder()
                .dictionaryId(DICTIONARY_ID)
                .userId(OWNER)
                .eventTimestamp(eventTimestamp)
                .build();
    }

    private static DictionaryVisibilityEvent privateEvent(OffsetDateTime updatedAt) {
        DictionaryVisibilityEvent event = publicEvent("Travel", updatedAt);
        event.setIsPublic(false);
        return event;
    }

    private static DictionaryVisibilityEvent publicEvent(String name, OffsetDateTime updatedAt) {
        return DictionaryVisibilityEvent.builder()
                .dictionaryId(DICTIONARY_ID)
                .userId(OWNER)
                .isPublic(true)
                .dictionaryName(name)
                .fromLang("EN")
                .toLang("DE")
                .updatedAt(updatedAt)
                .eventTimestamp(T3)
                .build();
    }

    private static DictionaryUpdatedEvent updatedEvent(String name, String toLang, OffsetDateTime updatedAt) {
        return DictionaryUpdatedEvent.builder()
                .dictionaryId(DICTIONARY_ID)
                .userId(OWNER)
                .dictionaryName(name)
                .fromLang("EN")
                .toLang(toLang)
                .updatedAt(updatedAt)
                .eventTimestamp(T3)
                .build();
    }

    private static DictionarySnapshotEntry entry(String dictionaryId, String name, OffsetDateTime updatedAt) {
        return DictionarySnapshotEntry.builder()
                .dictionaryId(dictionaryId)
                .userId(OWNER)
                .dictionaryName(name)
                .fromLang("EN")
                .toLang("DE")
                .updatedAt(updatedAt)
                .build();
    }

    private static DictionarySnapshotEvent snapshot(OffsetDateTime takenAt, DictionarySnapshotEntry... entries) {
        return DictionarySnapshotEvent.builder()
                .takenAt(takenAt)
                .dictionaries(List.of(entries))
                .eventTimestamp(takenAt)
                .build();
    }
}
