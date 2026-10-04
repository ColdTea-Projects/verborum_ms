"""
Dummy vocabulary for the dev seed, in five languages (en, de, tr, fr, es).

Every entry follows the word contract in docs/integration/frontend-backend-integration.md §4.2:
`word` / `translation` are JSON arrays of per-meaning surface forms (article included), and the meta
is {lang, type?, genders?, fields?} with every list index-aligned to the surfaces.

Shapes, per language:
  noun      (singular, plural or None, gender or None)       genders m/f/n
  verb/adj  ([surfaces...], {field: [values...]})            fields index-aligned to surfaces
  sentence  "text"                                           free text: no type, no fields
"""

LANGS = ("en", "de", "tr", "fr", "es")

# ---------------------------------------------------------------------------------------------
# Nouns, grouped by theme
# ---------------------------------------------------------------------------------------------
NOUNS = {
    "food": {
        "apple":  dict(en=("apple", "apples", None), de=("der Apfel", "die Äpfel", "m"), tr=("elma", "elmalar", None), fr=("la pomme", "les pommes", "f"), es=("la manzana", "las manzanas", "f")),
        "bread":  dict(en=("bread", None, None), de=("das Brot", "die Brote", "n"), tr=("ekmek", "ekmekler", None), fr=("le pain", "les pains", "m"), es=("el pan", "los panes", "m")),
        "cheese": dict(en=("cheese", "cheeses", None), de=("der Käse", "die Käse", "m"), tr=("peynir", "peynirler", None), fr=("le fromage", "les fromages", "m"), es=("el queso", "los quesos", "m")),
        "water":  dict(en=("water", None, None), de=("das Wasser", None, "n"), tr=("su", "sular", None), fr=("l'eau", "les eaux", "f"), es=("el agua", "las aguas", "f")),
        "coffee": dict(en=("coffee", "coffees", None), de=("der Kaffee", "die Kaffees", "m"), tr=("kahve", "kahveler", None), fr=("le café", "les cafés", "m"), es=("el café", "los cafés", "m")),
        "milk":   dict(en=("milk", None, None), de=("die Milch", None, "f"), tr=("süt", None, None), fr=("le lait", None, "m"), es=("la leche", None, "f")),
        "egg":    dict(en=("egg", "eggs", None), de=("das Ei", "die Eier", "n"), tr=("yumurta", "yumurtalar", None), fr=("l'œuf", "les œufs", "m"), es=("el huevo", "los huevos", "m")),
        "soup":   dict(en=("soup", "soups", None), de=("die Suppe", "die Suppen", "f"), tr=("çorba", "çorbalar", None), fr=("la soupe", "les soupes", "f"), es=("la sopa", "las sopas", "f")),
        "fish":   dict(en=("fish", "fish", None), de=("der Fisch", "die Fische", "m"), tr=("balık", "balıklar", None), fr=("le poisson", "les poissons", "m"), es=("el pescado", "los pescados", "m")),
        "meat":   dict(en=("meat", None, None), de=("das Fleisch", None, "n"), tr=("et", "etler", None), fr=("la viande", None, "f"), es=("la carne", "las carnes", "f")),
        "tomato": dict(en=("tomato", "tomatoes", None), de=("die Tomate", "die Tomaten", "f"), tr=("domates", "domatesler", None), fr=("la tomate", "les tomates", "f"), es=("el tomate", "los tomates", "m")),
        "potato": dict(en=("potato", "potatoes", None), de=("die Kartoffel", "die Kartoffeln", "f"), tr=("patates", "patatesler", None), fr=("la pomme de terre", "les pommes de terre", "f"), es=("la patata", "las patatas", "f")),
        "cake":   dict(en=("cake", "cakes", None), de=("der Kuchen", "die Kuchen", "m"), tr=("pasta", "pastalar", None), fr=("le gâteau", "les gâteaux", "m"), es=("el pastel", "los pasteles", "m")),
        "wine":   dict(en=("wine", "wines", None), de=("der Wein", "die Weine", "m"), tr=("şarap", "şaraplar", None), fr=("le vin", "les vins", "m"), es=("el vino", "los vinos", "m")),
    },
    "home": {
        "house":   dict(en=("house", "houses", None), de=("das Haus", "die Häuser", "n"), tr=("ev", "evler", None), fr=("la maison", "les maisons", "f"), es=("la casa", "las casas", "f")),
        "kitchen": dict(en=("kitchen", "kitchens", None), de=("die Küche", "die Küchen", "f"), tr=("mutfak", "mutfaklar", None), fr=("la cuisine", "les cuisines", "f"), es=("la cocina", "las cocinas", "f")),
        "table":   dict(en=("table", "tables", None), de=("der Tisch", "die Tische", "m"), tr=("masa", "masalar", None), fr=("la table", "les tables", "f"), es=("la mesa", "las mesas", "f")),
        "chair":   dict(en=("chair", "chairs", None), de=("der Stuhl", "die Stühle", "m"), tr=("sandalye", "sandalyeler", None), fr=("la chaise", "les chaises", "f"), es=("la silla", "las sillas", "f")),
        "bed":     dict(en=("bed", "beds", None), de=("das Bett", "die Betten", "n"), tr=("yatak", "yataklar", None), fr=("le lit", "les lits", "m"), es=("la cama", "las camas", "f")),
        "window":  dict(en=("window", "windows", None), de=("das Fenster", "die Fenster", "n"), tr=("pencere", "pencereler", None), fr=("la fenêtre", "les fenêtres", "f"), es=("la ventana", "las ventanas", "f")),
        "door":    dict(en=("door", "doors", None), de=("die Tür", "die Türen", "f"), tr=("kapı", "kapılar", None), fr=("la porte", "les portes", "f"), es=("la puerta", "las puertas", "f")),
        "key":     dict(en=("key", "keys", None), de=("der Schlüssel", "die Schlüssel", "m"), tr=("anahtar", "anahtarlar", None), fr=("la clé", "les clés", "f"), es=("la llave", "las llaves", "f")),
        "lamp":    dict(en=("lamp", "lamps", None), de=("die Lampe", "die Lampen", "f"), tr=("lamba", "lambalar", None), fr=("la lampe", "les lampes", "f"), es=("la lámpara", "las lámparas", "f")),
        "garden":  dict(en=("garden", "gardens", None), de=("der Garten", "die Gärten", "m"), tr=("bahçe", "bahçeler", None), fr=("le jardin", "les jardins", "m"), es=("el jardín", "los jardines", "m")),
    },
    "travel": {
        "ticket":   dict(en=("ticket", "tickets", None), de=("die Fahrkarte", "die Fahrkarten", "f"), tr=("bilet", "biletler", None), fr=("le billet", "les billets", "m"), es=("el billete", "los billetes", "m")),
        "train":    dict(en=("train", "trains", None), de=("der Zug", "die Züge", "m"), tr=("tren", "trenler", None), fr=("le train", "les trains", "m"), es=("el tren", "los trenes", "m")),
        "airport":  dict(en=("airport", "airports", None), de=("der Flughafen", "die Flughäfen", "m"), tr=("havalimanı", "havalimanları", None), fr=("l'aéroport", "les aéroports", "m"), es=("el aeropuerto", "los aeropuertos", "m")),
        "suitcase": dict(en=("suitcase", "suitcases", None), de=("der Koffer", "die Koffer", "m"), tr=("bavul", "bavullar", None), fr=("la valise", "les valises", "f"), es=("la maleta", "las maletas", "f")),
        "passport": dict(en=("passport", "passports", None), de=("der Reisepass", "die Reisepässe", "m"), tr=("pasaport", "pasaportlar", None), fr=("le passeport", "les passeports", "m"), es=("el pasaporte", "los pasaportes", "m")),
        "hotel":    dict(en=("hotel", "hotels", None), de=("das Hotel", "die Hotels", "n"), tr=("otel", "oteller", None), fr=("l'hôtel", "les hôtels", "m"), es=("el hotel", "los hoteles", "m")),
        "street":   dict(en=("street", "streets", None), de=("die Straße", "die Straßen", "f"), tr=("sokak", "sokaklar", None), fr=("la rue", "les rues", "f"), es=("la calle", "las calles", "f")),
        "map":      dict(en=("map", "maps", None), de=("die Landkarte", "die Landkarten", "f"), tr=("harita", "haritalar", None), fr=("la carte", "les cartes", "f"), es=("el mapa", "los mapas", "m")),
        "station":  dict(en=("station", "stations", None), de=("der Bahnhof", "die Bahnhöfe", "m"), tr=("istasyon", "istasyonlar", None), fr=("la gare", "les gares", "f"), es=("la estación", "las estaciones", "f")),
        "beach":    dict(en=("beach", "beaches", None), de=("der Strand", "die Strände", "m"), tr=("plaj", "plajlar", None), fr=("la plage", "les plages", "f"), es=("la playa", "las playas", "f")),
    },
    "animals": {
        "dog":   dict(en=("dog", "dogs", None), de=("der Hund", "die Hunde", "m"), tr=("köpek", "köpekler", None), fr=("le chien", "les chiens", "m"), es=("el perro", "los perros", "m")),
        "cat":   dict(en=("cat", "cats", None), de=("die Katze", "die Katzen", "f"), tr=("kedi", "kediler", None), fr=("le chat", "les chats", "m"), es=("el gato", "los gatos", "m")),
        "bird":  dict(en=("bird", "birds", None), de=("der Vogel", "die Vögel", "m"), tr=("kuş", "kuşlar", None), fr=("l'oiseau", "les oiseaux", "m"), es=("el pájaro", "los pájaros", "m")),
        "horse": dict(en=("horse", "horses", None), de=("das Pferd", "die Pferde", "n"), tr=("at", "atlar", None), fr=("le cheval", "les chevaux", "m"), es=("el caballo", "los caballos", "m")),
        "cow":   dict(en=("cow", "cows", None), de=("die Kuh", "die Kühe", "f"), tr=("inek", "inekler", None), fr=("la vache", "les vaches", "f"), es=("la vaca", "las vacas", "f")),
        "mouse": dict(en=("mouse", "mice", None), de=("die Maus", "die Mäuse", "f"), tr=("fare", "fareler", None), fr=("la souris", "les souris", "f"), es=("el ratón", "los ratones", "m")),
        "bear":  dict(en=("bear", "bears", None), de=("der Bär", "die Bären", "m"), tr=("ayı", "ayılar", None), fr=("l'ours", "les ours", "m"), es=("el oso", "los osos", "m")),
    },
    "work": {
        "office":    dict(en=("office", "offices", None), de=("das Büro", "die Büros", "n"), tr=("ofis", "ofisler", None), fr=("le bureau", "les bureaux", "m"), es=("la oficina", "las oficinas", "f")),
        "computer":  dict(en=("computer", "computers", None), de=("der Computer", "die Computer", "m"), tr=("bilgisayar", "bilgisayarlar", None), fr=("l'ordinateur", "les ordinateurs", "m"), es=("el ordenador", "los ordenadores", "m")),
        "meeting":   dict(en=("meeting", "meetings", None), de=("die Besprechung", "die Besprechungen", "f"), tr=("toplantı", "toplantılar", None), fr=("la réunion", "les réunions", "f"), es=("la reunión", "las reuniones", "f")),
        "colleague": dict(en=("colleague", "colleagues", None), de=("der Kollege", "die Kollegen", "m"), tr=("iş arkadaşı", "iş arkadaşları", None), fr=("le collègue", "les collègues", "m"), es=("el colega", "los colegas", "m")),
        "job":       dict(en=("job", "jobs", None), de=("die Arbeit", "die Arbeiten", "f"), tr=("iş", "işler", None), fr=("le travail", "les travaux", "m"), es=("el trabajo", "los trabajos", "m")),
        "money":     dict(en=("money", None, None), de=("das Geld", "die Gelder", "n"), tr=("para", "paralar", None), fr=("l'argent", None, "m"), es=("el dinero", None, "m")),
        "phone":     dict(en=("phone", "phones", None), de=("das Telefon", "die Telefone", "n"), tr=("telefon", "telefonlar", None), fr=("le téléphone", "les téléphones", "m"), es=("el teléfono", "los teléfonos", "m")),
    },
}

# Feminine forms where a noun has one — the `feminine` field
NOUN_FEMININE = {
    ("de", "colleague"): "die Kollegin", ("fr", "colleague"): "la collègue", ("es", "colleague"): "la colega",
    ("fr", "cat"): "la chatte", ("es", "cat"): "la gata", ("es", "dog"): "la perra", ("fr", "dog"): "la chienne",
}

# ---------------------------------------------------------------------------------------------
# Verbs — some with two meanings, to exercise index-aligned multi-meaning entries
# ---------------------------------------------------------------------------------------------
VERBS = {
    "buy":   dict(en=(["to buy", "to purchase"], dict(past=["bought", "purchased"], participle=["bought", "purchased"])),
                  de=(["kaufen", "erwerben"], dict(past=["kaufte", "erwarb"], participle=["gekauft", "erworben"], aux=["haben", "haben"])),
                  tr=(["satın almak", "almak"], dict(past=["satın aldı", "aldı"])),
                  fr=(["acheter"], dict(participle=["acheté"], aux=["avoir"])),
                  es=(["comprar", "adquirir"], dict(past=["compró", "adquirió"], participle=["comprado", "adquirido"]))),
    "eat":   dict(en=(["to eat"], dict(past=["ate"], participle=["eaten"])),
                  de=(["essen"], dict(present=["isst"], past=["aß"], participle=["gegessen"], aux=["haben"])),
                  tr=(["yemek"], dict(past=["yedi"])),
                  fr=(["manger"], dict(participle=["mangé"], aux=["avoir"])),
                  es=(["comer"], dict(past=["comió"], participle=["comido"]))),
    "drink": dict(en=(["to drink"], dict(past=["drank"], participle=["drunk"])),
                  de=(["trinken"], dict(past=["trank"], participle=["getrunken"], aux=["haben"])),
                  tr=(["içmek"], dict(past=["içti"])),
                  fr=(["boire"], dict(participle=["bu"], aux=["avoir"])),
                  es=(["beber", "tomar"], dict(past=["bebió", "tomó"], participle=["bebido", "tomado"]))),
    "go":    dict(en=(["to go"], dict(past=["went"], participle=["gone"])),
                  de=(["gehen", "fahren"], dict(past=["ging", "fuhr"], participle=["gegangen", "gefahren"], aux=["sein", "sein"])),
                  tr=(["gitmek"], dict(past=["gitti"])),
                  fr=(["aller"], dict(participle=["allé"], aux=["être"])),
                  es=(["ir"], dict(past=["fue"], participle=["ido"]))),
    "see":   dict(en=(["to see"], dict(past=["saw"], participle=["seen"])),
                  de=(["sehen"], dict(present=["sieht"], past=["sah"], participle=["gesehen"], aux=["haben"])),
                  tr=(["görmek"], dict(past=["gördü"])),
                  fr=(["voir"], dict(participle=["vu"], aux=["avoir"])),
                  es=(["ver"], dict(past=["vio"], participle=["visto"]))),
    "speak": dict(en=(["to speak", "to talk"], dict(past=["spoke", "talked"], participle=["spoken", "talked"])),
                  de=(["sprechen", "reden"], dict(present=["spricht", "redet"], past=["sprach", "redete"], participle=["gesprochen", "geredet"], aux=["haben", "haben"])),
                  tr=(["konuşmak"], dict(past=["konuştu"])),
                  fr=(["parler"], dict(participle=["parlé"], aux=["avoir"])),
                  es=(["hablar"], dict(past=["habló"], participle=["hablado"]))),
    "read":  dict(en=(["to read"], dict(past=["read"], participle=["read"])),
                  de=(["lesen"], dict(present=["liest"], past=["las"], participle=["gelesen"], aux=["haben"])),
                  tr=(["okumak"], dict(past=["okudu"])),
                  fr=(["lire"], dict(participle=["lu"], aux=["avoir"])),
                  es=(["leer"], dict(past=["leyó"], participle=["leído"]))),
    "write": dict(en=(["to write"], dict(past=["wrote"], participle=["written"])),
                  de=(["schreiben"], dict(past=["schrieb"], participle=["geschrieben"], aux=["haben"])),
                  tr=(["yazmak"], dict(past=["yazdı"])),
                  fr=(["écrire"], dict(participle=["écrit"], aux=["avoir"])),
                  es=(["escribir"], dict(past=["escribió"], participle=["escrito"]))),
    "work":  dict(en=(["to work"], dict(past=["worked"], participle=["worked"])),
                  de=(["arbeiten"], dict(past=["arbeitete"], participle=["gearbeitet"], aux=["haben"])),
                  tr=(["çalışmak"], dict(past=["çalıştı"])),
                  fr=(["travailler"], dict(participle=["travaillé"], aux=["avoir"])),
                  es=(["trabajar"], dict(past=["trabajó"], participle=["trabajado"]))),
    "sleep": dict(en=(["to sleep"], dict(past=["slept"], participle=["slept"])),
                  de=(["schlafen"], dict(present=["schläft"], past=["schlief"], participle=["geschlafen"], aux=["haben"])),
                  tr=(["uyumak"], dict(past=["uyudu"])),
                  fr=(["dormir"], dict(participle=["dormi"], aux=["avoir"])),
                  es=(["dormir"], dict(past=["durmió"], participle=["dormido"]))),
    "learn": dict(en=(["to learn"], dict(past=["learned"], participle=["learned"])),
                  de=(["lernen"], dict(past=["lernte"], participle=["gelernt"], aux=["haben"])),
                  tr=(["öğrenmek"], dict(past=["öğrendi"])),
                  fr=(["apprendre"], dict(participle=["appris"], aux=["avoir"])),
                  es=(["aprender"], dict(past=["aprendió"], participle=["aprendido"]))),
    "open":  dict(en=(["to open"], dict(past=["opened"], participle=["opened"])),
                  de=(["öffnen", "aufmachen"], dict(past=["öffnete", "machte auf"], participle=["geöffnet", "aufgemacht"], aux=["haben", "haben"])),
                  tr=(["açmak"], dict(past=["açtı"])),
                  fr=(["ouvrir"], dict(participle=["ouvert"], aux=["avoir"])),
                  es=(["abrir"], dict(past=["abrió"], participle=["abierto"]))),
}

# ---------------------------------------------------------------------------------------------
# Adjectives
# ---------------------------------------------------------------------------------------------
ADJECTIVES = {
    "big":       dict(en=(["big"], dict(comparative=["bigger"], superlative=["biggest"])),
                      de=(["groß"], dict(comparative=["größer"], superlative=["am größten"])),
                      tr=(["büyük"], dict(comparative=["daha büyük"], superlative=["en büyük"])),
                      fr=(["grand"], dict(feminine=["grande"], comparative=["plus grand"], superlative=["le plus grand"])),
                      es=(["grande"], dict(comparative=["más grande"], superlative=["el más grande"]))),
    "small":     dict(en=(["small"], dict(comparative=["smaller"], superlative=["smallest"])),
                      de=(["klein"], dict(comparative=["kleiner"], superlative=["am kleinsten"])),
                      tr=(["küçük"], dict(comparative=["daha küçük"], superlative=["en küçük"])),
                      fr=(["petit"], dict(feminine=["petite"], comparative=["plus petit"], superlative=["le plus petit"])),
                      es=(["pequeño"], dict(feminine=["pequeña"], comparative=["más pequeño"], superlative=["el más pequeño"]))),
    "good":      dict(en=(["good"], dict(comparative=["better"], superlative=["best"])),
                      de=(["gut"], dict(comparative=["besser"], superlative=["am besten"])),
                      tr=(["iyi"], dict(comparative=["daha iyi"], superlative=["en iyi"])),
                      fr=(["bon"], dict(feminine=["bonne"], comparative=["meilleur"], superlative=["le meilleur"])),
                      es=(["bueno"], dict(feminine=["buena"], comparative=["mejor"], superlative=["el mejor"]))),
    "beautiful": dict(en=(["beautiful", "pretty"], dict(comparative=["more beautiful", "prettier"], superlative=["most beautiful", "prettiest"])),
                      de=(["schön", "hübsch"], dict(comparative=["schöner", "hübscher"], superlative=["am schönsten", "am hübschesten"])),
                      tr=(["güzel"], dict(comparative=["daha güzel"], superlative=["en güzel"])),
                      fr=(["beau", "joli"], dict(feminine=["belle", "jolie"], comparative=["plus beau", "plus joli"], superlative=["le plus beau", "le plus joli"])),
                      es=(["bonito", "hermoso"], dict(feminine=["bonita", "hermosa"], comparative=["más bonito", "más hermoso"], superlative=["el más bonito", "el más hermoso"]))),
    "cheap":     dict(en=(["cheap"], dict(comparative=["cheaper"], superlative=["cheapest"])),
                      de=(["billig", "günstig"], dict(comparative=["billiger", "günstiger"], superlative=["am billigsten", "am günstigsten"])),
                      tr=(["ucuz"], dict(comparative=["daha ucuz"], superlative=["en ucuz"])),
                      fr=(["bon marché"], dict(comparative=["meilleur marché"], superlative=["le meilleur marché"])),
                      es=(["barato"], dict(feminine=["barata"], comparative=["más barato"], superlative=["el más barato"]))),
    "expensive": dict(en=(["expensive"], dict(comparative=["more expensive"], superlative=["most expensive"])),
                      de=(["teuer"], dict(comparative=["teurer"], superlative=["am teuersten"])),
                      tr=(["pahalı"], dict(comparative=["daha pahalı"], superlative=["en pahalı"])),
                      fr=(["cher"], dict(feminine=["chère"], comparative=["plus cher"], superlative=["le plus cher"])),
                      es=(["caro"], dict(feminine=["cara"], comparative=["más caro"], superlative=["el más caro"]))),
    "fast":      dict(en=(["fast", "quick"], dict(comparative=["faster", "quicker"], superlative=["fastest", "quickest"])),
                      de=(["schnell"], dict(comparative=["schneller"], superlative=["am schnellsten"])),
                      tr=(["hızlı"], dict(comparative=["daha hızlı"], superlative=["en hızlı"])),
                      fr=(["rapide"], dict(comparative=["plus rapide"], superlative=["le plus rapide"])),
                      es=(["rápido"], dict(feminine=["rápida"], comparative=["más rápido"], superlative=["el más rápido"]))),
    "new":       dict(en=(["new"], dict(comparative=["newer"], superlative=["newest"])),
                      de=(["neu"], dict(comparative=["neuer"], superlative=["am neuesten"])),
                      tr=(["yeni"], dict(comparative=["daha yeni"], superlative=["en yeni"])),
                      fr=(["nouveau"], dict(feminine=["nouvelle"], comparative=["plus nouveau"], superlative=["le plus nouveau"])),
                      es=(["nuevo"], dict(feminine=["nueva"], comparative=["más nuevo"], superlative=["el más nuevo"]))),
}

# ---------------------------------------------------------------------------------------------
# Sentences — short ones, long ones (100–200 chars) and two very long ones (300+ chars)
# ---------------------------------------------------------------------------------------------
SENTENCES = {
    "station": dict(en="Where is the train station?", de="Wo ist der Bahnhof?", tr="Tren istasyonu nerede?",
                    fr="Où est la gare ?", es="¿Dónde está la estación de tren?"),
    "slowly": dict(en="Could you please speak a little more slowly? I am still learning the language.",
                   de="Könnten Sie bitte etwas langsamer sprechen? Ich lerne die Sprache noch.",
                   tr="Lütfen biraz daha yavaş konuşabilir misiniz? Bu dili hâlâ öğreniyorum.",
                   fr="Pourriez-vous parler un peu plus lentement, s'il vous plaît ? J'apprends encore la langue.",
                   es="¿Podría hablar un poco más despacio, por favor? Todavía estoy aprendiendo el idioma."),
    "room": dict(en="I would like to book a double room with a view of the sea for three nights, from Friday to Monday.",
                 de="Ich möchte ein Doppelzimmer mit Meerblick für drei Nächte buchen, von Freitag bis Montag.",
                 tr="Cumadan pazartesiye kadar üç gece için deniz manzaralı bir çift kişilik oda ayırtmak istiyorum.",
                 fr="Je voudrais réserver une chambre double avec vue sur la mer pour trois nuits, du vendredi au lundi.",
                 es="Me gustaría reservar una habitación doble con vistas al mar para tres noches, de viernes a lunes."),
    "price": dict(en="Excuse me, how much is this?", de="Entschuldigung, wie viel kostet das?", tr="Affedersiniz, bu ne kadar?",
                  fr="Excusez-moi, combien ça coûte ?", es="Perdone, ¿cuánto cuesta esto?"),
    "childhood": dict(
        en="When I was a child, we spent every summer at my grandparents' house in a small village near the coast, where we swam in the sea every morning and ate fresh fish every evening.",
        de="Als ich ein Kind war, verbrachten wir jeden Sommer im Haus meiner Großeltern in einem kleinen Dorf an der Küste, wo wir jeden Morgen im Meer schwammen und jeden Abend frischen Fisch aßen.",
        tr="Çocukken her yazı sahile yakın küçük bir köyde, büyükannemle büyükbabamın evinde geçirirdik; her sabah denizde yüzer, her akşam taze balık yerdik.",
        fr="Quand j'étais enfant, nous passions chaque été dans la maison de mes grands-parents, dans un petit village près de la côte, où nous nagions dans la mer tous les matins et mangions du poisson frais tous les soirs.",
        es="Cuando era niño, pasábamos todos los veranos en la casa de mis abuelos, en un pequeño pueblo cerca de la costa, donde nadábamos en el mar cada mañana y comíamos pescado fresco cada noche."),
    "understand": dict(en="I don't understand.", de="Ich verstehe das nicht.", tr="Anlamıyorum.",
                       fr="Je ne comprends pas.", es="No entiendo."),
    "meeting": dict(
        en="The meeting has been moved to Thursday afternoon because half of the team is travelling on Wednesday; please update your calendars and let me know if the new time does not work for you.",
        de="Die Besprechung wurde auf Donnerstagnachmittag verschoben, weil die Hälfte des Teams am Mittwoch unterwegs ist; bitte aktualisiert eure Kalender und sagt mir Bescheid, falls der neue Termin für euch nicht passt.",
        tr="Ekibin yarısı çarşamba günü seyahatte olduğu için toplantı perşembe öğleden sonraya ertelendi; lütfen takvimlerinizi güncelleyin ve yeni saat size uymuyorsa bana haber verin.",
        fr="La réunion a été déplacée au jeudi après-midi parce que la moitié de l'équipe est en déplacement mercredi ; merci de mettre à jour vos agendas et de me prévenir si le nouvel horaire ne vous convient pas.",
        es="La reunión se ha trasladado al jueves por la tarde porque la mitad del equipo está de viaje el miércoles; por favor, actualizad vuestros calendarios y avisadme si el nuevo horario no os viene bien."),
    "card": dict(en="Can I pay by card?", de="Kann ich mit Karte bezahlen?", tr="Kartla ödeyebilir miyim?",
                 fr="Est-ce que je peux payer par carte ?", es="¿Puedo pagar con tarjeta?"),
    "patience": dict(
        en="Learning a new language takes time and patience: at first every sentence feels like a puzzle, then one day you notice that you understood a whole conversation on the bus without translating a single word in your head, and that is the moment you realise that all the small daily steps, the flashcards, the mistakes and the repetitions were worth it.",
        de="Eine neue Sprache zu lernen braucht Zeit und Geduld: Am Anfang fühlt sich jeder Satz wie ein Rätsel an, dann merkst du eines Tages, dass du im Bus ein ganzes Gespräch verstanden hast, ohne ein einziges Wort im Kopf zu übersetzen, und in diesem Moment wird dir klar, dass sich all die kleinen täglichen Schritte, die Karteikarten, die Fehler und die Wiederholungen gelohnt haben.",
        tr="Yeni bir dil öğrenmek zaman ve sabır ister: başta her cümle bir bulmaca gibi gelir, sonra bir gün otobüste kafanda tek bir kelimeyi bile çevirmeden koca bir konuşmayı anladığını fark edersin ve işte o an, bütün o küçük günlük adımların, kelime kartlarının, hataların ve tekrarların buna değdiğini anlarsın.",
        fr="Apprendre une nouvelle langue demande du temps et de la patience : au début, chaque phrase ressemble à une énigme, puis un jour tu remarques que tu as compris toute une conversation dans le bus sans traduire un seul mot dans ta tête, et c'est à ce moment-là que tu comprends que tous les petits pas quotidiens, les fiches, les erreurs et les répétitions en valaient la peine.",
        es="Aprender un idioma nuevo requiere tiempo y paciencia: al principio cada frase parece un rompecabezas, y un día te das cuenta de que has entendido toda una conversación en el autobús sin traducir ni una sola palabra en tu cabeza, y en ese momento comprendes que todos los pequeños pasos diarios, las tarjetas, los errores y las repeticiones han valido la pena."),
    "directions": dict(
        en="Go straight ahead until you reach the big square with the fountain, then turn left after the bakery, cross the bridge over the river and you will see the museum on your right, just opposite the old post office; it takes about fifteen minutes on foot.",
        de="Gehen Sie geradeaus, bis Sie zum großen Platz mit dem Brunnen kommen, biegen Sie dann nach der Bäckerei links ab, überqueren Sie die Brücke über den Fluss, und Sie sehen das Museum auf der rechten Seite, direkt gegenüber der alten Post; zu Fuß dauert es etwa fünfzehn Minuten.",
        tr="Çeşmeli büyük meydana varana kadar dümdüz gidin, sonra fırından sonra sola dönün, nehrin üzerindeki köprüyü geçin; müzeyi sağ tarafta, eski postanenin tam karşısında göreceksiniz; yürüyerek yaklaşık on beş dakika sürer.",
        fr="Allez tout droit jusqu'à la grande place avec la fontaine, puis tournez à gauche après la boulangerie, traversez le pont sur la rivière et vous verrez le musée sur votre droite, juste en face de l'ancienne poste ; il faut environ quinze minutes à pied.",
        es="Siga recto hasta llegar a la plaza grande con la fuente, luego gire a la izquierda después de la panadería, cruce el puente sobre el río y verá el museo a su derecha, justo enfrente de la antigua oficina de correos; se tarda unos quince minutos a pie."),
    "tomorrow": dict(en="See you tomorrow!", de="Bis morgen!", tr="Yarın görüşürüz!", fr="À demain !", es="¡Hasta mañana!"),
}


# ---------------------------------------------------------------------------------------------
# Builders: one concept in one language → (surfaces, meta)
# ---------------------------------------------------------------------------------------------
def _meta(lang, type_=None, genders=None, fields=None):
    meta = {"lang": lang}
    if type_:
        meta["type"] = type_
    if genders and any(genders):
        meta["genders"] = genders
    if fields:
        meta["fields"] = {k: v for k, v in fields.items() if v and any(v)}
        if not meta["fields"]:
            del meta["fields"]
    return meta


def noun(theme, key, lang):
    singular, plural, gender = NOUNS[theme][key][lang]
    fields = {}
    if plural:
        fields["plural"] = [plural]
    feminine = NOUN_FEMININE.get((lang, key))
    if feminine:
        fields["feminine"] = [feminine]
    return [singular], _meta(lang, "noun", [gender] if gender else None, fields)


def verb(key, lang):
    surfaces, fields = VERBS[key][lang]
    return list(surfaces), _meta(lang, "verb", None, fields)


def adjective(key, lang):
    surfaces, fields = ADJECTIVES[key][lang]
    return list(surfaces), _meta(lang, "adjective", None, fields)


def sentence(key, lang):
    return [SENTENCES[key][lang]], _meta(lang)


def concepts(theme):
    """Every concept of a theme as (builder, key) pairs."""
    if theme in NOUNS:
        return [("noun", theme, key) for key in NOUNS[theme]]
    if theme == "verbs":
        return [("verb", None, key) for key in VERBS]
    if theme == "adjectives":
        return [("adjective", None, key) for key in ADJECTIVES]
    if theme == "phrases":
        return [("sentence", None, key) for key in SENTENCES]
    raise ValueError(theme)


def build(concept, lang):
    kind, theme, key = concept
    if kind == "noun":
        return noun(theme, key, lang)
    if kind == "verb":
        return verb(key, lang)
    if kind == "adjective":
        return adjective(key, lang)
    return sentence(key, lang)


THEMES = ["food", "home", "travel", "animals", "work", "verbs", "adjectives", "phrases"]

ALL_LEXICAL = [c for theme in ["food", "home", "travel", "animals", "work", "verbs", "adjectives"] for c in concepts(theme)]
