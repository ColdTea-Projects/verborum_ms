package de.coldtea.verborum.msdictionary.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on `@Scheduled` for the `dictionary.snapshot` publisher (P4-03).
 * <p>
 * Every instance runs the schedule. That is correct while there is one instance; once ms_dictionary
 * is scaled out (Phase 5+) the snapshot needs a lock such as ShedLock so only one instance sends it.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
