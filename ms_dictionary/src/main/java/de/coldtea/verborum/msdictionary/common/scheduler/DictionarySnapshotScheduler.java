package de.coldtea.verborum.msdictionary.common.scheduler;

import de.coldtea.verborum.msdictionary.dictionary.service.DictionaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static de.coldtea.verborum.msdictionary.common.constants.ErrorMessageConstants.SNAPSHOT_PUBLISH_FAILED;

/**
 * Publishes the `dictionary.snapshot` reconciliation event on a schedule (rule 6, P4-03).
 * <p>
 * A trigger only, like a listener: it delegates to one service method and holds no logic. Nightly by
 * default (`DICTIONARY_SNAPSHOT_CRON`). The snapshot repairs <i>lost</i> events — normal listing
 * changes reach the marketplace within seconds through their own events, so the interval is how long
 * a lost event can leave a listing wrong, not how long new dictionaries take to appear.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DictionarySnapshotScheduler {

    private final DictionaryService dictionaryService;

    @Scheduled(cron = "${dictionary.snapshot.cron}")
    public void publishSnapshot() {
        log.info("Publishing dictionary.snapshot");
        try {
            dictionaryService.publishPublicSnapshot();
        } catch (Exception e) {
            // Not re-thrown: there is no caller to fail, and an exception escaping a @Scheduled
            // method is only logged by Spring anyway. The next run is the retry.
            log.error(SNAPSHOT_PUBLISH_FAILED, e);
        }
    }
}
