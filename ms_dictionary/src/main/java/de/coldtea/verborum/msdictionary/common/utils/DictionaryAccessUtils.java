package de.coldtea.verborum.msdictionary.common.utils;

import de.coldtea.verborum.msdictionary.dictionary.entity.Dictionary;

/**
 * The read rule for a dictionary and everything under it — its words and its tags (roadmap P4-10).
 * <p>
 * <b>Readable = owned by the caller, or public.</b> Until P4-10 only the owner could read, which left
 * a dictionary imported from the marketplace impossible to open: ms_user's vault holds a reference,
 * and the importer's client reads the content from here.
 * <p>
 * One place on purpose. The three services that read dictionaries (dictionary, word, tag) must agree —
 * if one of them drifted, a private dictionary's words could be readable while the dictionary itself
 * 404s. A non-readable dictionary still 404s exactly like a missing one (P3-08), so its existence is not
 * revealed. Writes are unaffected: they stay owner-only (403).
 */
public class DictionaryAccessUtils {

    private DictionaryAccessUtils() {
    }

    public static boolean isReadableBy(Dictionary dictionary, String callerId) {
        // Boolean.TRUE.equals: a null is_public is private, never public
        return callerId.equals(dictionary.getUserId()) || Boolean.TRUE.equals(dictionary.getIsPublic());
    }
}
