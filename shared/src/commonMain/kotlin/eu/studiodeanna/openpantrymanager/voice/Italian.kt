package eu.studiodeanna.openpantrymanager.voice

/** Past participles in all four endings: "mangiat" gives mangiato, mangiata, mangiati, mangiate. */
private fun participles(vararg stems: String) = stems.flatMap { stem -> listOf("o", "a", "i", "e").map { stem + it } }.toSet()

/** Voice commands in Italian: "ho mangiato una mela", "ho spostato il latte in frigorifero". */
val Italian = VoiceLanguage(
    code = "it",
    model = SpeechModel(
        name = "vosk-model-small-it-0.22",
        url = "https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip",
        sha256 = "9ec65e75861d1c6c2e457cccd932705340dcdf233f5b239f00733b4de0bf3267",
        bytes = 49_665_141,
    ),
    use = participles("mangiat", "bevut", "usat", "consumat", "cucinat", "utilizzat") +
        setOf("mangio", "mangiamo", "bevo", "beviamo", "uso", "usiamo", "consumo", "consumiamo", "cucino", "cuciniamo"),
    finish = participles("finit", "terminat", "esaurit") + setOf("finisco", "finiamo"),
    add = participles("comprat", "aggiunt", "mess", "acquistat") +
        setOf("compro", "compriamo", "aggiungo", "aggiungiamo", "metto", "mettiamo"),
    move = participles("spostat", "trasferit", "portat") + setOf("sposto", "spostiamo", "trasferisco", "porto", "portiamo"),
    numbers = mapOf(
        "un" to 1, "uno" to 1, "una" to 1, "due" to 2, "tre" to 3, "quattro" to 4, "cinque" to 5, "sei" to 6,
        "sette" to 7, "otto" to 8, "nove" to 9, "dieci" to 10, "undici" to 11, "dodici" to 12, "tredici" to 13,
        "quattordici" to 14, "quindici" to 15, "sedici" to 16, "diciassette" to 17, "diciotto" to 18,
        "diciannove" to 19, "venti" to 20, "trenta" to 30,
    ),
    all = setOf("tutto", "tutta", "tutti", "tutte"),
    fillers = setOf(
        // "o" and "a" are what the recognizer hears for "ho" and "ha" at times.
        "io", "noi", "ho", "o", "abbiamo", "hai", "ha", "a", "hanno", "e", "appena", "ancora", "anche", "gia", "ne", "mi", "ci",
        "il", "lo", "la", "i", "gli", "le", "l", "di", "del", "della", "dello", "dei", "degli", "delle", "d", "po",
    ),
    containers = setOf(
        "confezione", "confezioni", "scatola", "scatole", "scatoletta", "scatolette", "pacco", "pacchi", "pacchetto",
        "pacchetti", "bottiglia", "bottiglie", "barattolo", "barattoli", "lattina", "lattine", "busta", "buste",
        "bustina", "bustine", "vasetto", "vasetti", "pezzo", "pezzi", "cartone", "cartoni", "sacchetto", "sacchetti",
        "vaschetta", "vaschette", "rotolo", "rotoli", "tubetto", "tubetti", "brick", "fetta", "fette", "tavoletta",
        "tavolette", "sacco", "sacchi", "bicchiere", "bicchieri", "tazza", "tazze", "chilo", "chili", "litro", "litri",
    ),
    into = setOf(
        "in", "nel", "nella", "nello", "nei", "negli", "nelle", "al", "alla", "allo", "ai", "agli", "alle", "dentro",
        "sul", "sulla", "sullo", "sui", "sugli", "sulle",
    ),
    from = setOf("da", "dal", "dalla", "dallo", "dai", "dagli", "dalle"),
    // Singular and plural, masculine and feminine differ in the last vowel: mela, mele; uovo, uova.
    stem = { word -> if (word.length > 3 && word.last() in "aeio") word.dropLast(1) else word },
    foods = (
        "uovo uova latte pane pasta riso mela mele banana banane pera pere arancia arance limone limoni fragole uva " +
            "pesca pesche kiwi ananas melone anguria albicocche ciliegie prugne frutta verdura insalata lattuga " +
            "pomodoro pomodori patata patate carota carote cipolla cipolle aglio zucchina zucchine melanzana melanzane " +
            "peperone peperoni spinaci fagioli fagiolini piselli ceci lenticchie funghi broccoli cavolo cavolfiore " +
            "sedano finocchio zucca mais yogurt burro formaggio formaggi mozzarella parmigiano grana ricotta stracchino " +
            "panna uova prosciutto salame mortadella speck bresaola salsiccia salsicce carne manzo maiale pollo tacchino " +
            "pesce tonno salmone merluzzo gamberi acciughe sardine olio aceto sale pepe zucchero farina lievito miele " +
            "marmellata nutella caffè tè tisana cacao cioccolato biscotti cracker grissini fette biscottate cereali " +
            "muesli passata sugo pelati pesto maionese ketchup senape brodo dado acqua frizzante naturale vino birra " +
            "succo succhi aranciata bibita gelato surgelati piselli spaghetti penne fusilli rigatoni gnocchi lasagne " +
            "pizza piadina tortellini ravioli focaccia brioche merendine torta"
        ).split(" ").toSet(),
    examples = listOf("ho mangiato una mela", "ho comprato sei uova", "ho spostato il latte in frigorifero", "ho finito la pasta"),
)
