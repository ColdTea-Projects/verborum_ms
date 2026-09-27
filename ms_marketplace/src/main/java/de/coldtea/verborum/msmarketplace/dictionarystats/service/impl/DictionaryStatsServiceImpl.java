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

/**
 * Every write here is "apply this state if it is newer than what I hold" (rule 4), with the
 * dictionary's own `updatedAt` as the clock. Visibility is part of that state: going private hides
 * the row rather than deleting it, so the hidden row's `sourceUpdatedAt` keeps rejecting older
 * "public" state — a delayed public event, or a snapshot read just before the flip. Deleting it
 * would leave nothing to compare against, and the older state would re-list a dictionary its owner
 * had just made private.
 */
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
            dictionaryStatsRepository.saveAndFlush(newRow(event.getDictionaryId(), event.getUserId(),
                    event.getDictionaryName(), event.getFromLang(), event.getToLang(), sourceUpdatedAt, true));
            return;
        }

        // Upsert, never a second insert (rule 3) — and a redelivered or overtaken event changes nothing
        DictionaryStats listing = existing.get();
        if (!isNewer(sourceUpdatedAt, listing)) {
            return;
        }

        applyPublicState(listing, event.getUserId(), event.getDictionaryName(), event.getFromLang(),
                event.getToLang(), sourceUpdatedAt);
        dictionaryStatsRepository.saveAndFlush(listing);
    }

    @Transactional
    @Override
    public void hideListing(DictionaryVisibilityEvent event) {
        OffsetDateTime sourceUpdatedAt = sourceUpdatedAt(event.getUpdatedAt(), event.getEventTimestamp());
        Optional<DictionaryStats> existing = dictionaryStatsRepository.findById(event.getDictionaryId());

        // No row yet: the private event overtook the public one, or the public one was lost. Record a
        // hidden row anyway — it is what makes that older public event compare as stale when it lands
        if (existing.isEmpty()) {
            dictionaryStatsRepository.saveAndFlush(newRow(event.getDictionaryId(), event.getUserId(),
                    event.getDictionaryName(), event.getFromLang(), event.getToLang(), sourceUpdatedAt, false));
            return;
        }

        DictionaryStats listing = existing.get();
        if (!isNewer(sourceUpdatedAt, listing)) {
            return;
        }

        applyListingFields(listing, event.getUserId(), event.getDictionaryName(), event.getFromLang(),
                event.getToLang(), sourceUpdatedAt);
        // importCount and publishedAt are kept: they are history, and survive a later re-publish
        listing.setIsListed(false);
        dictionaryStatsRepository.saveAndFlush(listing);
    }

    @Transactional
    @Override
    public void updateListing(DictionaryUpdatedEvent event) {
        OffsetDateTime sourceUpdatedAt = sourceUpdatedAt(event.getUpdatedAt(), event.getEventTimestamp());

        // Update-only on purpose: see the interface. filter() drops stale deliveries (rule 4).
        // A newer update on a hidden row re-lists it: ms_dictionary only sends dictionary.updated for a
        // dictionary that is public, so it proves the dictionary went public again after it was hidden
        // — even if that public event has not arrived (or was lost)
        dictionaryStatsRepository.findById(event.getDictionaryId())
                .filter(listing -> isNewer(sourceUpdatedAt, listing))
                .ifPresent(listing -> {
                    applyPublicState(listing, event.getUserId(), event.getDictionaryName(), event.getFromLang(),
                            event.getToLang(), sourceUpdatedAt);
                    dictionaryStatsRepository.saveAndFlush(listing);
                });
    }

    /**
     * One pass, one transaction: the snapshot is the complete set of public dictionaries at
     * `takenAt`, so whatever this service holds can be compared against it directly.
     * <p>
     * A dictionary made private in the seconds between the snapshot query and this method is safe:
     * its hidden row's `sourceUpdatedAt` is newer than the snapshot entry, so the entry is skipped.
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
                toSave.add(newRow(entry.getDictionaryId(), entry.getUserId(), entry.getDictionaryName(),
                        entry.getFromLang(), entry.getToLang(), sourceUpdatedAt, true));
            } else if (isNewer(sourceUpdatedAt, listing)) {
                applyPublicState(listing, entry.getUserId(), entry.getDictionaryName(), entry.getFromLang(),
                        entry.getToLang(), sourceUpdatedAt);
                toSave.add(listing);
            }
        }

        Set<String> inSnapshot = entries.stream()
                .map(DictionarySnapshotEntry::getDictionaryId)
                .collect(Collectors.toSet());

        // Absent from the snapshot means "not public at takenAt" — but only for a row whose own state
        // predates takenAt. One made public a moment after the query is legitimately missing and must
        // survive until the next snapshot includes it.
        //
        // Hidden rows older than takenAt are removed too. A hidden row only has to outlive the public
        // state older than it that is still in flight, and after a snapshot taken later than the row
        // nothing that old is left in normal delivery. The one exception is a manual replay from the
        // DLQ, which could re-list a private dictionary until the next snapshot hides it again —
        // accepted, against keeping one row forever for every dictionary ever made private
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
        // seeing in the log, since it is the only signal that the event path has a leak. Removed
        // hidden rows are routine cleanup, not a leak
        log.info("dictionary.snapshot reconciled: {} public, {} created or corrected, {} removed",
                entries.size(), toSave.size(), toRemove.size());
    }

    private static DictionaryStats newRow(String dictionaryId, String userId, String name, String fromLang,
                                          String toLang, OffsetDateTime sourceUpdatedAt, boolean listed) {
        return DictionaryStats.builder()
                .dictionaryId(dictionaryId)
                .userId(userId)
                .name(name)
                .fromLang(fromLang)
                .toLang(toLang)
                // Both explicit — the column defaults do not apply through Hibernate (see the entity)
                .isListed(listed)
                .importCount(0)
                // Best available "went public" time: the change that made it public is the latest
                // change ms_dictionary reports for it. For a hidden row it is a placeholder, replaced
                // when the row is listed
                .publishedAt(sourceUpdatedAt)
                .sourceUpdatedAt(sourceUpdatedAt)
                .build();
    }

    /**
     * The newer state says "public": apply it, and re-list the row if it was hidden. A re-listed
     * row counts as newly published.
     */
    private static void applyPublicState(DictionaryStats listing, String userId, String name, String fromLang,
                                         String toLang, OffsetDateTime sourceUpdatedAt) {
        if (!Boolean.TRUE.equals(listing.getIsListed())) {
            listing.setIsListed(true);
            listing.setPublishedAt(sourceUpdatedAt);
        }
        applyListingFields(listing, userId, name, fromLang, toLang, sourceUpdatedAt);
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
