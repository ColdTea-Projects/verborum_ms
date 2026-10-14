package de.coldtea.verborum.msnotification.push;

/**
 * A silent data message (decided 2026-10-14): no title, no body, nothing the OS shows. `type` tells the
 * app what to do (`SYNC`, `REFRESH`), `dictionaryId` is an id only — never a name or any content — and
 * `collapseKey` lets FCM and APNs keep just the latest pending message of a burst.
 */
public record PushMessage(String type, String dictionaryId, String collapseKey) {

    public static final String TYPE_SYNC = "SYNC";
    public static final String TYPE_REFRESH = "REFRESH";
}
