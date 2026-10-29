package eu.studiodeanna.openpantrymanager.voice

/** Past participles in all four endings: "mangiat" gives mangiato, mangiata, mangiati, mangiate. */
private fun participles(vararg stems: String) = stems.flatMap { stem -> listOf("o", "a", "i", "e").map { stem + it } }.toSet()

/** Given or received: who gives says which. */
private val given = participles("dat", "regalat")

/** Who has, before the verb: "ho", "abbiamo", "hanno"; "o" and "a" as the recognizer hears "ho" and "ha" at times. */
private val has = listOf("ho", "o", "hai", "ha", "a", "abbiamo", "avete", "hanno")

/**
 * Given to the speaker: "mi hanno dato", "ci ha regalato", "me le hanno regalate", "mi è stato dato";
 * brought too ("mi hanno portato"), not moved.
 */
private val received = (given + participles("portat")).let { verbs ->
    val gave = has.filter { it != "ho" && it != "o" && it != "abbiamo" }
    listOf("mi", "ci").flatMap { to -> gave.flatMap { h -> verbs.map { "$to $h $it" } } } +
        listOf("me", "ce").flatMap { to ->
            listOf("l", "lo", "la", "li", "le", "ne").flatMap { what -> gave.flatMap { h -> verbs.map { "$to $what $h $it" } } }
        } +
        listOf("mi", "ci").flatMap { to -> listOf("e", "sono").flatMap { be -> participles("stat").flatMap { been -> given.map { "$to $be $been $it" } } } }
}

/** Voice commands in Italian: "ho mangiato una mela", "ho spostato il latte in frigorifero". */
val Italian = VoiceLanguage(
    code = "it",
    model = SpeechModel(
        name = "vosk-model-small-it-0.22",
        url = "https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip",
        sha256 = "9ec65e75861d1c6c2e457cccd932705340dcdf233f5b239f00733b4de0bf3267",
        bytes = 49_665_141,
    ),
    use = participles("mangiat", "bevut", "usat", "consumat", "cucinat", "utilizzat", "buttat", "gettat", "tolt", "levat") +
        participles("buttat", "gettat").map { "$it via" } +
        // Given away: "ho dato", "abbiamo regalato", "gli ho dato"; given to the speaker is under add.
        has.flatMap { h -> given.map { "$h $it" } } +
        setOf(
            "mangio", "mangiamo", "bevo", "beviamo", "uso", "usiamo", "consumo", "consumiamo", "cucino", "cuciniamo", "butto",
            "buttiamo", "tolgo", "togliamo",
            // Said to the app: "togli due uova".
            "togli", "usa", "consuma", "scala", "sottrai", "rimuovi", "leva",
        ),
    finish = participles("finit", "terminat", "esaurit") +
        participles("rimast").flatMap { listOf("non e $it", "non sono $it") } +
        setOf(
            "finisco", "finiamo", "manca", "mancano", "elimina", "cancella", "non ho piu", "non abbiamo piu", "non c e piu",
            "non ce n e piu", "non ci sono piu", "non ce ne sono piu",
        ),
    add = participles("comprat", "aggiunt", "mess", "acquistat", "ricevut", "ripost") +
        participles("mess").map { "$it via" } +
        received +
        setOf("compro", "compriamo", "aggiungo", "aggiungiamo", "metto", "mettiamo", "aggiungi", "metti", "inserisci"),
    move = participles("spostat", "trasferit", "portat") +
        setOf("sposto", "spostiamo", "trasferisco", "porto", "portiamo", "sposta", "trasferisci", "porta"),
    take = participles("pres") + setOf("prendo", "prendiamo"),
    units = mapOf(
        "grammi" to grams(1.0), "grammo" to grams(1.0), "gr" to grams(1.0), "etto" to grams(100.0), "etti" to grams(100.0),
        "chilo" to grams(1000.0), "chili" to grams(1000.0), "chilogrammi" to grams(1000.0), "kg" to grams(1000.0),
        "millilitri" to millilitres(1.0), "ml" to millilitres(1.0), "litro" to millilitres(1000.0), "litri" to millilitres(1000.0),
    ),
    // "Ho iniziato i biscotti": a new packet started is opened.
    open = participles("apert", "iniziat", "cominciat") + setOf("apro", "apriamo", "apri", "inizio", "iniziamo", "comincio", "cominciamo"),
    numbers = mapOf(
        "un" to 1, "uno" to 1, "una" to 1, "due" to 2, "tre" to 3, "quattro" to 4, "cinque" to 5, "sei" to 6,
        "sette" to 7, "otto" to 8, "nove" to 9, "dieci" to 10, "undici" to 11, "dodici" to 12, "tredici" to 13,
        "quattordici" to 14, "quindici" to 15, "sedici" to 16, "diciassette" to 17, "diciotto" to 18,
        "diciannove" to 19, "venti" to 20, "ventuno" to 21, "ventidue" to 22, "ventitre" to 23, "ventiquattro" to 24,
        "venticinque" to 25, "ventisei" to 26, "ventisette" to 27, "ventotto" to 28, "ventinove" to 29, "trenta" to 30,
        "quaranta" to 40, "cinquanta" to 50, "cento" to 100, "paio" to 2, "coppia" to 2, "dozzina" to 12,
    ),
    all = setOf("tutto", "tutta", "tutti", "tutte"),
    fillers = setOf(
        // "o" and "a" are what the recognizer hears for "ho" and "ha" at times.
        "io", "noi", "ho", "o", "abbiamo", "hai", "ha", "a", "hanno", "e", "appena", "ancora", "anche", "gia", "ne", "mi", "ci",
        "il", "lo", "la", "i", "gli", "le", "l", "di", "del", "della", "dello", "dei", "degli", "delle", "d", "po",
        "ed", "sono", "era", "erano", "stato", "stata", "stati", "state", "si", "ce", "c", "oggi", "ieri", "stamattina",
        "stamani", "stasera", "adesso", "ora", "poi", "pure", "proprio", "altro", "altra", "altri", "altre", "qualche",
        "alcuni", "alcune", "per", "questo", "questa", "questi", "queste", "quello", "quella", "quelli", "quelle",
        // Where it was bought is not part of its name: "il latte al supermercato".
        "supermercato", "supermercati", "ipermercato", "negozio", "mercato", "alimentari", "discount",
    ),
    containers = setOf(
        "confezione", "confezioni", "scatola", "scatole", "scatoletta", "scatolette", "pacco", "pacchi", "pacchetto",
        "pacchetti", "bottiglia", "bottiglie", "barattolo", "barattoli", "lattina", "lattine", "busta", "buste",
        "bustina", "bustine", "vasetto", "vasetti", "pezzo", "pezzi", "cartone", "cartoni", "sacchetto", "sacchetti",
        "vaschetta", "vaschette", "rotolo", "rotoli", "tubetto", "tubetti", "brick", "fetta", "fette", "tavoletta",
        "tavolette", "sacco", "sacchi", "bicchiere", "bicchieri", "tazza", "tazze", "chilo", "chili", "litro", "litri",
        "flacone", "flaconi", "rete", "reti", "mazzo", "mazzi", "cespo", "cespi", "panetto", "panetti", "etto", "etti",
        "grammi", "grappolo", "grappoli", "spicchio", "spicchi", "porzione", "porzioni", "barattolino", "barattolini",
        "scatolone", "scatoloni", "teglia", "teglie",
    ),
    into = setOf(
        "in", "nel", "nella", "nello", "nei", "negli", "nelle", "al", "alla", "allo", "ai", "agli", "alle", "dentro",
        "sul", "sulla", "sullo", "sui", "sugli", "sulle",
    ),
    from = setOf("da", "dal", "dalla", "dallo", "dai", "dagli", "dalle"),
    // Singular and plural, masculine and feminine differ in the last vowel: mela, mele; uovo, uova.
    stem = { word -> if (word.length > 3 && word.last() in "aeio") word.dropLast(1) else word },
    // Not "tè": listening for it, the recognizer hears "tre" as "tè" ("regalato tè mele"). A pantry with tea in it adds it.
    foods = (
        "uovo uova latte pane pasta riso mela mele banana banane pera pere arancia arance limone limoni fragole uva " +
            "pesca pesche kiwi ananas melone anguria albicocche ciliegie prugne frutta verdura insalata lattuga " +
            "pomodoro pomodori patata patate carota carote cipolla cipolle aglio zucchina zucchine melanzana melanzane " +
            "peperone peperoni spinaci fagioli fagiolini piselli ceci lenticchie funghi broccoli cavolo cavolfiore " +
            "sedano finocchio zucca mais yogurt burro formaggio formaggi mozzarella parmigiano grana ricotta stracchino " +
            "panna uova prosciutto salame mortadella speck bresaola salsiccia salsicce carne manzo maiale pollo tacchino " +
            "pesce tonno salmone merluzzo gamberi acciughe sardine olio aceto sale pepe zucchero farina lievito miele " +
            "marmellata nutella caffè tisana cacao cioccolato biscotti cracker grissini fette biscottate cereali " +
            "muesli passata sugo pelati pesto maionese ketchup senape brodo dado acqua frizzante naturale vino birra " +
            "succo succhi aranciata bibita gelato surgelati piselli spaghetti penne fusilli rigatoni gnocchi lasagne " +
            "pizza piadina tortellini ravioli focaccia brioche merendine torta " +
            // Singulars, for the recognizer to hear them too.
            "fragola ciliegia prugna albicocca fungo fagiolo pisello lenticchia biscotto merendina cornetto panino " +
            "asparagi carciofi carciofo rucola radicchio porri cetrioli cetriolo olive oliva capperi noci noce mandorle " +
            "nocciole pistacchi arachidi uvetta datteri fichi mirtilli lamponi mandarini mandarino clementine pompelmo " +
            "avocado mango cocco polenta orzo farro quinoa couscous pangrattato mascarpone gorgonzola pecorino fontina " +
            "scamorza provola emmental feta wurstel pancetta guanciale cotechino hamburger polpette cotolette bistecca " +
            "bistecche vitello agnello coniglio trota orata branzino calamari cozze vongole polpo seppie sgombro baccalà " +
            "toast pancarrè taralli plumcake croissant cornetti budino panettone pandoro colomba caramelle patatine " +
            "popcorn cola camomilla ragù besciamella origano basilico prezzemolo rosmarino salvia cannella vaniglia " +
            "zafferano curry paprika peperoncino margarina " +
            // What else a pantry keeps.
            "detersivo sapone dentifricio shampoo carta igienica tovaglioli fazzoletti spugne candeggina ammorbidente " +
            "pannolini crocchette"
        ).split(" ").toSet(),
    examples = listOf("ho mangiato una mela", "ho comprato sei uova", "ho spostato il latte in frigorifero", "ho aperto i biscotti", "ho finito la pasta"),
    spoken = setOf("più", "c'è", "n'è", "è", "già", "po'", "l'ho", "l'", "un'", "dell'", "nell'", "all'", "dall'", "sull'", "ventitré"),
)
