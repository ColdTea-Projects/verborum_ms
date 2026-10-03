package de.coldtea.verborum.msmarketplace.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Published on `dictionary.snapshot` on a schedule (nightly by default): every public dictionary in
 * one message. The reconciliation backstop of rule 6 — ms_marketplace diffs its listings against it
 * to repair anything a lost event left wrong (roadmap P4-03).
 * <p>
 * One message, not one per dictionary, so the consumer sees the complete set at once and can tell
 * "absent" from "not delivered yet". Roughly 200 bytes per entry; chunk it if public dictionaries
 * ever number in the hundreds of thousands — and then the consumer can no longer diff in one pass.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionarySnapshotEvent {

    /**
     * Taken immediately <b>before</b> the query. A listing the consumer holds whose `updatedAt` is
     * newer than this changed after the snapshot was read — e.g. a dictionary made public a second
     * later — so its absence from `dictionaries` proves nothing and it must not be deleted.
     * Same clock as every entry's `updatedAt` (this service's), so the comparison is meaningful.
     */
    private OffsetDateTime takenAt;

    private List<DictionarySnapshotEntry> dictionaries;

    private OffsetDateTime eventTimestamp;
}
