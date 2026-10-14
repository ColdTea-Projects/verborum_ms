package de.coldtea.verborum.msnotification.push;

import java.util.List;

/**
 * What a send learned. `invalidTokens` are addresses FCM says will never work again (the app was
 * uninstalled or the token rotated) and must be deleted. `transientFailures` counts tokens that failed
 * for a reason a retry can fix (quota, outage); the caller throws so the broker redelivers.
 */
public record PushResult(List<String> invalidTokens, int transientFailures) {

    public static PushResult none() {
        return new PushResult(List.of(), 0);
    }

    public boolean hasTransientFailures() {
        return transientFailures > 0;
    }
}
