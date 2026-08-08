# Constants and Utility Classes

## Constants — no inline strings

Every message and every numeric field limit lives in a constants class, as
`public static final`. Three of them, split by audience:

| Class | Holds | Example |
|---|---|---|
| `DTOMessageConstants` | Bean Validation messages **and** the numeric field limits | `DICTIONARY_NAME`, `DICTIONARY_NAME_MAX` |
| `ErrorMessageConstants` | service and exception messages | `DICTIONARY_WAS_NOT_FOUND_ID`, `NOT_THE_OWNER` |
| `ResponseMessageConstants` | controller success messages | `DICTIONARY_SAVED_SUCCESSFULLY` |

Validation messages must be compile-time constants — annotation attributes cannot take anything
else. That is exactly why the limits (`WORD_TEXT_MAX`, `WORD_META_MAX`, `WORD_LEVEL_MIN`,
`WORD_LEVEL_MAX`) live in `DTOMessageConstants` alongside the messages rather than in a config
class.

Import them statically:

```java
import static de.coldtea.verborum.msdictionary.common.constants.DTOMessageConstants.*;
```

Error and success message constants end with a separator where an id is appended by the caller:

```java
public static final String DICTIONARY_WAS_NOT_FOUND_ID = "Dictionary not found for ID: ";
public static final String DICTIONARY_SAVED_SUCCESSFULLY = "Dictionary saved successfully: ";
```

## Utility classes

Static-only, with a **private constructor** so they cannot be instantiated:

```java
public class SecurityUtils {

    private SecurityUtils() {
    }

    public static String getCurrentUserId() { ... }
}
```

`ResponseUtils`, `ListUtils` and `SecurityUtils` all follow this shape.

## Static imports at the call site

Controllers read better this way, and the existing code does it consistently:

```java
import static de.coldtea.verborum.msdictionary.common.constants.ResponseMessageConstants.*;
import static de.coldtea.verborum.msdictionary.common.utils.ResponseUtils.buildResponse;
import static de.coldtea.verborum.msdictionary.common.utils.SecurityUtils.getCurrentUserId;
import static de.coldtea.verborum.msdictionary.common.utils.SecurityUtils.requireSelf;
```

## Locale

Case conversion always takes an explicit locale:

```java
private static String normalise(String tag) {
    return tag.trim().toLowerCase(Locale.ROOT);
}
```

Default-locale `toLowerCase()` mangles the Turkish dotted/dotless `I`, and `TR` is one of the 19
supported languages — so this is a live hazard here, not a theoretical one. Tag normalisation
applies on write **and** on delete, so removing `Food` removes what `food` stored.
