package de.coldtea.verborum.msmarketplace.dictionarystats.service.impl;

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

    // ---- updateListing (dictionary.updated) ----

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
                .importCount(0)
                .publishedAt(T1)
                .sourceUpdatedAt(sourceUpdatedAt)
                .build();
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
