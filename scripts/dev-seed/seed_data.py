"""
Who the dummy users are and what they own. Deterministic: the same seed always produces the same plan.

Language pairs are (word language, translation language): a Turkish speaker learning German has
("de", "tr"). Most users learn one language; a few learn two or three.
"""
import random

from vocab import ALL_LEXICAL, SENTENCES, THEMES, concepts

PASSWORD = "test1234"
EMAIL_DOMAIN = "example.com"
TERMS_VERSION = "2026-10-01"

# display name, member?, language pairs, number of dictionaries, how many to hide after joining
USERS = [
    ("Anna Bauer",     True,  [("tr", "de")],                           10, 0),
    ("Mehmet Yılmaz",  True,  [("de", "tr")],                           12, 2),
    ("Elif Kaya",      True,  [("en", "tr")],                            8, 0),
    ("Lukas Schmidt",  True,  [("en", "de")],                           25, 0),
    ("Sophie Martin",  True,  [("en", "fr"), ("de", "fr")],             14, 1),
    ("Carlos García",  True,  [("en", "es")],                            5, 0),
    ("Emma Johnson",   True,  [("es", "en"), ("fr", "en"), ("de", "en")], 18, 0),
    ("Zeynep Demir",   True,  [("de", "tr")],                            9, 0),
    ("Jonas Weber",    True,  [("en", "de")],                           11, 3),
    ("Léa Dubois",     True,  [("de", "fr")],                            7, 0),
    ("Ahmet Çelik",    True,  [("en", "tr"), ("de", "tr")],             13, 0),
    ("María López",    True,  [("en", "es")],                           10, 0),
    ("Tom Becker",     True,  [("en", "de")],                            6, 0),
    ("Oliver Brown",   False, [("fr", "en")],                            8, 0),
    ("Nina Koch",      False, [("es", "de")],                           10, 0),
    # Second batch (added 2026-10-04) — appended, so the users above keep exactly the same plan
    ("Hannah Fischer",   True,  [("en", "de")],                            9, 0),
    ("Can Öztürk",       True,  [("de", "tr")],                           11, 1),
    ("Selin Arslan",     True,  [("en", "tr")],                           10, 0),
    ("Max Wagner",       True,  [("es", "de")],                            7, 0),
    ("Chloé Bernard",    True,  [("en", "fr")],                           12, 1),
    ("Pablo Fernández",  True,  [("de", "es"), ("en", "es")],             15, 0),
    ("Olivia Smith",     True,  [("fr", "en")],                           10, 0),
    ("Burak Şahin",      True,  [("en", "tr")],                            5, 0),
    ("Felix Hoffmann",   True,  [("fr", "de"), ("en", "de")],             20, 2),
    ("Camille Petit",    True,  [("es", "fr")],                            8, 0),
    ("Ece Aydın",        True,  [("de", "tr"), ("en", "tr"), ("fr", "tr")], 16, 0),
    ("Lucía Martínez",   True,  [("de", "es")],                            9, 0),
    ("James Wilson",     True,  [("de", "en")],                           22, 0),
    ("Mia Schulz",       False, [("tr", "de")],                            7, 0),
    ("Kerem Doğan",      False, [("de", "tr")],                           12, 0),
]

_ASCII = str.maketrans("ıçğöşüéáíóúñèêàâäëïîôûÿœ", "icgosueaioun" "eeaaaeiiouyo")


def username(display_name):
    """'Mehmet Yılmaz' -> 'mehmet.yilmaz' (also the email local part)."""
    first, last = display_name.lower().split(" ", 1)
    return f"{first}.{last}".translate(_ASCII).replace(" ", "")


def email(display_name):
    return f"{username(display_name)}@{EMAIL_DOMAIN}"


SEED_USERNAMES = [username(user[0]) for user in USERS]

# Dictionary names per theme — several, so a user with many dictionaries does not repeat one
NAMES = {
    "food": ["Food & Drinks", "Kitchen Basics", "At the Market", "Breakfast Words", "Cooking at Home"],
    "home": ["Around the House", "My Home", "Furniture", "Rooms and Things"],
    "travel": ["Travel Essentials", "At the Airport", "Holiday Words", "Getting Around", "City Trip"],
    "animals": ["Animals", "Farm Animals", "Pets & Wildlife"],
    "work": ["Office Talk", "At Work", "Business Basics", "My First Job"],
    "verbs": ["Everyday Verbs", "Top Verbs", "Action Words", "Irregular Verbs"],
    "adjectives": ["Describing Things", "Adjectives", "Comparisons"],
    "phrases": ["Useful Phrases", "Conversation Starters", "Small Talk", "Survival Phrases"],
}

TAGS = {
    "food": ["food", "kitchen", "a1"], "home": ["home", "a1"], "travel": ["travel", "holiday", "a2"],
    "animals": ["animals", "a1"], "work": ["work", "business", "b1"], "verbs": ["verbs", "grammar"],
    "adjectives": ["adjectives", "grammar"], "phrases": ["phrases", "conversation"],
}

# The dictionaries that should top "popular": each covers a case the clients want to test, and is
# imported by `importers` members. Keyed by the owner's display name.
SHOWCASES = {
    "Lukas Schmidt": dict(
        name="The Big Vocabulary Collection", pair=("en", "de"), concepts=ALL_LEXICAL,
        tags=["vocabulary", "essentials", "a1", "a2", "beginner"], importers=20,
        case="many words (68)"),
    "Anna Bauer": dict(
        name="Long Sentences: Reading Practice", pair=("tr", "de"),
        concepts=[("sentence", None, key) for key in SENTENCES], tags=["sentences", "reading", "b2"],
        importers=18, case="long inputs (sentences up to 400 characters)"),
    "Emma Johnson": dict(
        name="Everything I Need for My First Trip to Spain: Airport, Hotel, Restaurant and Small Talk Phrases",
        pair=("es", "en"),
        concepts=concepts("travel") + [("sentence", None, k) for k in ["station", "room", "price", "card", "directions", "tomorrow"]],
        tags=["travel", "holiday", "spain", "restaurant", "hotel", "phrases", "a2"], importers=16,
        case="very long name, many tags, words mixed with sentences"),
    "Mehmet Yılmaz": dict(
        name="Verbs with Several Meanings", pair=("de", "tr"), concepts=concepts("verbs"),
        tags=["verbs", "grammar", "b1"], importers=12, case="multi-meaning entries with index-aligned fields"),
    "Sophie Martin": dict(
        name="Comparisons and Feminine Forms", pair=("en", "fr"), concepts=concepts("adjectives") + concepts("animals"),
        tags=["adjectives", "grammar", "animals"], importers=8, case="feminine and comparative fields"),
}


def plan(seed=42):
    """
    The full, deterministic plan: per user, their dictionaries (name, pair, concept list, tags,
    a showcase flag) and how many to hide after joining. Showcases come first in each user's list.
    """
    rng = random.Random(seed)
    users = []
    for display_name, member, pairs, count, hide in USERS:
        dictionaries = []
        showcase = SHOWCASES.get(display_name)
        if showcase:
            dictionaries.append(dict(name=showcase["name"], pair=showcase["pair"], concepts=list(showcase["concepts"]),
                                     tags=showcase["tags"], showcase=True))

        used_names = set()
        while len(dictionaries) < count:
            # Most dictionaries in the first pair; the rest spread over the others
            pair = pairs[0] if len(pairs) == 1 or rng.random() < 0.6 else rng.choice(pairs[1:])
            theme = rng.choice(THEMES)
            theme_concepts = concepts(theme)
            # Mostly 6–15 words; a whole theme sometimes
            size = len(theme_concepts) if rng.random() < 0.25 else rng.randint(6, max(6, min(15, len(theme_concepts))))
            chosen = rng.sample(theme_concepts, min(size, len(theme_concepts)))
            # Some lexical dictionaries get a couple of sentences mixed in
            if theme != "phrases" and rng.random() < 0.3:
                chosen += [("sentence", None, key) for key in rng.sample(list(SENTENCES), 2)]

            name = rng.choice(NAMES[theme])
            if name in used_names:
                name = f"{name} {len([n for n in used_names if n.startswith(name)]) + 1}"
            used_names.add(name)

            dictionaries.append(dict(name=name, pair=pair, concepts=chosen,
                                     tags=rng.sample(TAGS[theme], rng.randint(1, len(TAGS[theme]))), showcase=False))

        users.append(dict(display_name=display_name, username=username(display_name), email=email(display_name),
                          member=member, dictionaries=dictionaries, hide=hide))
    return users
