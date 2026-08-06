package de.coldtea.verborum.msdictionary.common.constants;

public final class DTOMessageConstants {

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

    //Dictionary DTOs
    public static final String DICTIONARY_ID = "dictionaryId";
    public static final String USER_ID = "userId";
    public static final String DICTIONARY_DICTIONARY_ID = "dictionaryId is mandatory";
    public static final String DICTIONARY_USER_ID = "userId is mandatory";
    public static final String DICTIONARY_NAME = "name is mandatory";
    public static final String DICTIONARY_NAME_TOO_LONG = "name must not exceed " + DICTIONARY_NAME_MAX + " characters";
    public static final String DICTIONARY_IS_PUBLIC = "isPublic is mandatory";
    public static final String DICTIONARY_FROM_LANG = "fromLang is mandatory";
    public static final String DICTIONARY_TO_LANG = "toLang is mandatory";
    public static final String INVALID_LANGUAGE_CODE = "Invalid language code: ";
    public static final String WORD_ID = "wordId";

    //Dictionary tag DTOs
    public static final String TAG_TAG = "tag is mandatory";


}
