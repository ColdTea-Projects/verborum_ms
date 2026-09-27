package de.coldtea.verborum.msmarketplace.dictionarystats.service.impl;

import de.coldtea.verborum.msmarketplace.common.event.DictionarySnapshotEntry;
import de.coldtea.verborum.msmarketplace.common.event.DictionarySnapshotEvent;
import de.coldtea.verborum.msmarketplace.common.event.DictionaryUpdatedEvent;
import de.coldtea.verborum.msmarketplace.common.event.DictionaryVisibilityEvent;
import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import de.coldtea.verborum.msmarketplace.dictionarystats.repository.DictionaryStatsRepository;
import de.coldtea.verborum.msmarketplace.dictionarystats.service.DictionaryStatsService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DictionaryStatsServiceImpl implements DictionaryStatsService {

    private final DictionaryStatsRepository dictionaryStatsRepository;

    @Transactional
    @Override
    public void publishListing(DictionaryVisibilityEvent event) {
        OffsetDateTime sourceUpdatedAt = sourceUpdatedAt(event.getUpdatedAt(), event.getEventTimestamp());
        Optional<DictionaryStats> existing = dictionaryStatsRepository.findById(event.getDictionaryId());

        if (existing.isEmpty()) {
            dictionaryStatsRepository.saveAndFlush(newListing(event.getDictionaryId(), event.getUserId(),
                    event.getDictionaryName(), event.getFromLang(), event.getToLang(), sourceUpdatedAt));
            return;
        }

        // Upsert, never a second insert (rule 3) — and a redelivered or overtaken event changes nothing
        DictionaryStats listing = existing.get();
        if (!isNewer(sourceUpdatedAt, listing)) {
            return;
        }

        applyListingFields(listing, event.getUserId(), event.getDictionaryName(), event.getFromLang(),
                event.getToLang(), sourceUpdatedAt);
        dictionaryStatsRepository.saveAndFlush(listing);
    }

    @Transactional
    @Override
    public void updateListing(DictionaryUpdatedEvent event) {
        OffsetDateTime sourceUpdatedAt = sourceUpdatedAt(event.getUpdatedAt(), event.getEventTimestamp());

        // Update-only on purpose: see the interface. filter() drops stale deliveries (rule 4)
        dictionaryStatsRepository.findById(event.getDictionaryId())
                .filter(listing -> isNewer(sourceUpdatedAt, listing))
                .ifPresent(listing -> {
                    applyListingFields(listing, event.getUserId(), event.getDictionaryName(), event.getFromLang(),
                            event.getToLang(), sourceUpdatedAt);
                    dictionaryStatsRepository.saveAndFlush(listing);
                });
    }

    /**
     * One pass, one transaction: the snapshot is the complete set of public dictionaries at
     * `takenAt`, so whatever this service holds can be compared against it directly.
     * <p>
     * Known gap, for P4-04 to close: a dictionary made private in the seconds between the snapshot
     * query and this method has already had its listing removed, so it is "missing" here and gets
     * recreated until the next snapshot. Removing a listing leaves nothing behind to compare against;
     * P4-04 keeping a hidden row (with its `sourceUpdatedAt`) instead of deleting would close it.
     */
    @Transactional
    @Override
    public void reconcile(DictionarySnapshotEvent event) {
        OffsetDateTime takenAt = event.getTakenAt();
        List<DictionarySnapshotEntry> entries = Optional.ofNullable(event.getDictionaries()).orElse(List.of());

        Map<String, DictionaryStats> held = dictionaryStatsRepository.findAll().stream()
                .collect(Collectors.toMap(DictionaryStats::getDictionaryId, Function.identity()));

        List<DictionaryStats> toSave = new ArrayList<>();
        for (DictionarySnapshotEntry entry : entries) {
            OffsetDateTime sourceUpdatedAt = sourceUpdatedAt(entry.getUpdatedAt(), takenAt);
            DictionaryStats listing = held.get(entry.getDictionaryId());

            if (listing == null) {
                toSave.add(newListing(entry.getDictionaryId(), entry.getUserId(), entry.getDictionaryName(),
                        entry.getFromLang(), entry.getToLang(), sourceUpdatedAt));
            } else if (isNewer(sourceUpdatedAt, listing)) {
                applyListingFields(listing, entry.getUserId(), entry.getDictionaryName(), entry.getFromLang(),
                        entry.getToLang(), sourceUpdatedAt);
                toSave.add(listing);
            }
        }

        Set<String> inSnapshot = entries.stream()
                .map(DictionarySnapshotEntry::getDictionaryId)
                .collect(Collectors.toSet());

        // Absent from the snapshot means "not public at takenAt" — but only for a listing whose own
        // state predates takenAt. One made public a moment after the query is legitimately missing
        // and must survive until the next snapshot includes it
        List<DictionaryStats> toRemove = held.values().stream()
                .filter(listing -> !inSnapshot.contains(listing.getDictionaryId()))
                .filter(listing -> listing.getSourceUpdatedAt().isBefore(takenAt))
                .toList();

        dictionaryStatsRepository.saveAllAndFlush(toSave);
        // deleteAllInBatch issues one DELETE ... WHERE dictionary_id IN (...). A derived deleteByXIn
        // would select the rows again and remove them one statement at a time
        if (!toRemove.isEmpty()) {
            dictionaryStatsRepository.deleteAllInBatch(toRemove);
        }

        // Anything non-zero here means an event was lost or overtaken since the last run — worth
        // seeing in the log, since it is the only signal that the event path has a leak
        log.info("dictionary.snapshot reconciled: {} public, {} created or corrected, {} removed",
                entries.size(), toSave.size(), toRemove.size());
    }

    private static DictionaryStats newListing(String dictionaryId, String userId, String name, String fromLang,
                                              String toLang, OffsetDateTime sourceUpdatedAt) {
        return DictionaryStats.builder()
                .dictionaryId(dictionaryId)
                .userId(userId)
                .name(name)
                .fromLang(fromLang)
                .toLang(toLang)
                // Explicit — the column default does not apply through Hibernate (see the entity)
                .importCount(0)
                // Best available "went public" time: the change that made it public is the latest
                // change ms_dictionary reports for it. Kept on later updates, never overwritten
                .publishedAt(sourceUpdatedAt)
                .sourceUpdatedAt(sourceUpdatedAt)
                .build();
    }

    private static void applyListingFields(DictionaryStats listing, String userId, String name, String fromLang,
                                           String toLang, OffsetDateTime sourceUpdatedAt) {
        listing.setUserId(userId);
        listing.setName(name);
        listing.setFromLang(fromLang);
        listing.setToLang(toLang);
        listing.setSourceUpdatedAt(sourceUpdatedAt);
    }

    /**
     * Rule 4: only strictly newer state is applied. Equal counts as stale, so the same event
     * delivered twice is a no-op.
     * <p>
     * Postgres keeps microseconds while an event can carry nanoseconds, so a redelivery may compare as
     * a hair newer than the stored value and re-apply identical fields. Harmless — same values —
     * and not worth a rounding rule that would have to match Postgres's exactly.
     */
    private static boolean isNewer(OffsetDateTime incoming, DictionaryStats listing) {
        return incoming.isAfter(listing.getSourceUpdatedAt());
    }

    /**
     * `update_dt` is nullable in ms_dictionary, so a row that predates it can arrive without an
     * `updatedAt`. The fallback keeps `source_updated_at` NOT NULL; the cost is at most one redundant
     * re-apply of identical values on a later delivery.
     */
    private static OffsetDateTime sourceUpdatedAt(OffsetDateTime updatedAt, OffsetDateTime fallback) {
        return Objects.requireNonNullElse(updatedAt, fallback);
    }
}
