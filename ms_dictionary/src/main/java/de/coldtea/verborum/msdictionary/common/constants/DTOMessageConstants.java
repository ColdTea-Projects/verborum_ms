package de.coldtea.verborum.msdictionary.common.constants;

public final class DTOMessageConstants {

    //@ValidUUID (P4-09) — the field name is prepended by the error handler
    public static final String INVALID_UUID = "must be a valid UUID";

    // Field limits. These are contract limits, not storage limits - the columns are TEXT.
    //
    // Note what they are NOT: a cap on a typed word. The clients cap typing at 40 characters per
    // surface (150 for free text and the reading note - docs/word-input-filtering-android.md), but
    // what arrives here is the serialised structure, not one surface: `word` and `translation` are
    // a JSON array of per-meaning surfaces (`["kaufen","erwerben"]`) and neither client caps how
    // many meanings a word may carry. So these bound the whole blob generously - enough for a word
    // with far more meanings than anyone writes by hand, while still refusing an unbounded body.
    public static final int WORD_TEXT_MAX = 2000;

    // wordMeta/translationMeta hold {lang, type?, genders?, fields?} with every list index-aligned
    // to the surfaces array, so the meta grows with the meaning count and is structurally larger
    // than the surfaces it describes - hence the wider bound.
    public static final int WORD_META_MAX = 4000;

    // Practice progress. The clients clamp to this range and treat anything outside it as corrupt
    // data to be healed - the healing only works because the range is the same on both sides.
    public static final int WORD_LEVEL_MIN = 0;
    public static final int WORD_LEVEL_MAX = 7;

    // Matches the dictionaries.name column, which is still VARCHAR(255). Without this a longer name
    // reaches Postgres and fails as a 500 instead of a 400 naming the field.
    public static final int DICTIONARY_NAME_MAX = 255;

    // SEC-07: collection sizes. The field limits above bound one word; nothing bounded how many came
    // at once — 5,000 words in one POST /words held a request thread for 14 s. Both clients send one
    // word per request and never call the batch reads, so these are far above any real use.
    public static final int WORD_BUNDLES_MAX = 5;
    public static final int WORDS_PER_BUNDLE_MAX = 500;
    public static final int BATCH_IDS_MAX = 100;

    // SEC-07: per-owner totals, checked when something new is created (an edit never trips them).
    // Generous for a personal vocabulary; they exist so one account cannot grow the database unbounded.
    public static final int DICTIONARIES_PER_USER_MAX = 1000;
    public static final int WORDS_PER_DICTIONARY_MAX = 5000;

    //Word DTOs
    public static final String WORD_WORD_ID = "wordId is mandatory";
    public static final String WORD_DICTIONARY_ID = "dictionaryId is mandatory";
    public static final String WORD_WORD = "word is mandatory";
    public static final String WORD_WORD_LIST = "list of words is mandatory";
    public static final String WORD_WORD_META = "wordMeta is mandatory";
    public static final String WORD_WORD_TRANSLATION = "translation is mandatory";
    public static final String WORD_WORD_TRANSLATION_META = "translationMeta is mandatory";
    public static final String WORD_WORD_TOO_LONG = "word must not exceed " + WORD_TEXT_MAX + " characters";
    public static final String WORD_WORD_TRANSLATION_TOO_LONG = "translation must not exceed " + WORD_TEXT_MAX + " characters";
    public static final String WORD_WORD_META_TOO_LONG = "wordMeta must not exceed " + WORD_META_MAX + " characters";
    public static final String WORD_WORD_TRANSLATION_META_TOO_LONG = "translationMeta must not exceed " + WORD_META_MAX + " characters";
    public static final String WORD_LEVEL_OUT_OF_RANGE = "level must be between " + WORD_LEVEL_MIN + " and " + WORD_LEVEL_MAX;
    public static final String TOO_MANY_WORD_BUNDLES = "at most " + WORD_BUNDLES_MAX + " bundles per request";
    public static final String TOO_MANY_WORDS_IN_BUNDLE = "at most " + WORDS_PER_BUNDLE_MAX + " words per bundle";
    public static final String TOO_MANY_IDS = "at most " + BATCH_IDS_MAX + " ids per request";

    //Dictionary DTOs
    public static final String DICTIONARY_DICTIONARY_ID = "dictionaryId is mandatory";
    public static final String DICTIONARY_USER_ID = "userId is mandatory";
    public static final String DICTIONARY_NAME = "name is mandatory";
    public static final String DICTIONARY_NAME_TOO_LONG = "name must not exceed " + DICTIONARY_NAME_MAX + " characters";
    public static final String DICTIONARY_IS_PUBLIC = "isPublic is mandatory";
    public static final String DICTIONARY_FROM_LANG = "fromLang is mandatory";
    public static final String DICTIONARY_TO_LANG = "toLang is mandatory";
    // The field or parameter name is prepended by the error handler; the value is not echoed back
    public static final String INVALID_LANGUAGE_CODE = "unsupported language code";

    //Dictionary tag DTOs
    public static final String TAG_TAG = "tag is mandatory";


}
