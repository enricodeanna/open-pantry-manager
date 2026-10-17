package eu.studiodeanna.openpantrymanager.voice

/** Voice commands in English: "I ate an apple", "I moved the milk to the fridge". */
val English = VoiceLanguage(
    code = "en",
    model = SpeechModel(
        name = "vosk-model-small-en-us-0.15",
        url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
        sha256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498",
        bytes = 41_205_931,
    ),
    // The recognizer often hears the present for the past ("I eat an apple"): both count.
    use = setOf(
        "ate", "eat", "eats", "eaten", "drank", "drink", "drinks", "drunk", "used", "use", "uses", "consumed",
        "consume", "consumes", "had", "cooked", "cook", "cooks",
    ),
    finish = setOf("finished", "finish", "finishes", "emptied", "ran out", "run out", "used up", "use up"),
    add = setOf("bought", "buy", "buys", "added", "add", "adds", "put", "puts", "got", "get", "purchased", "stocked"),
    move = setOf("moved", "move", "moves", "transferred", "transfer"),
    numbers = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "dozen" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
        "twenty" to 20, "thirty" to 30,
    ),
    all = setOf("all", "everything"),
    fillers = setOf("i", "we", "you", "have", "has", "just", "also", "already", "the", "of", "some", "my", "our", "it", "them", "me"),
    containers = setOf(
        "box", "boxes", "pack", "packs", "packet", "packets", "bottle", "bottles", "can", "cans", "jar", "jars", "bag",
        "bags", "piece", "pieces", "carton", "cartons", "tin", "tins", "tub", "tubs", "slice", "slices", "loaf",
        "loaves", "bar", "bars", "cup", "cups", "glass", "glasses", "pound", "pounds", "kilo", "kilos", "liter",
        "liters", "litre", "litres",
    ),
    into = setOf("in", "into", "to", "inside", "on", "onto"),
    from = setOf("from", "out"),
    stem = { word ->
        when {
            word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
            word.length > 4 && (word.endsWith("oes") || word.endsWith("ches") || word.endsWith("shes") || word.endsWith("xes") || word.endsWith("sses")) ->
                word.dropLast(2)
            word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
            else -> word
        }
    },
    foods = (
        "egg eggs milk bread pasta rice apple apples banana bananas pear pears orange oranges lemon lemons strawberries " +
            "grapes peach peaches kiwi pineapple melon watermelon cherries plums fruit vegetables salad lettuce tomato " +
            "tomatoes potato potatoes carrot carrots onion onions garlic zucchini eggplant pepper peppers spinach beans " +
            "peas chickpeas lentils mushrooms broccoli cabbage cauliflower celery pumpkin corn yogurt yoghurt butter " +
            "cheese mozzarella parmesan cream ham salami bacon sausage sausages meat beef pork chicken turkey fish tuna " +
            "salmon cod shrimp prawns anchovies oil vinegar salt sugar flour yeast honey jam coffee tea cocoa chocolate " +
            "cookies biscuits crackers cereal cereals oats sauce tomato pesto mayonnaise ketchup mustard stock water " +
            "sparkling wine beer juice soda ice cream frozen spaghetti noodles pizza pie cake muffins"
        ).split(" ").toSet(),
    examples = listOf("I ate an apple", "I bought six eggs", "I moved the milk to the fridge", "I finished the pasta"),
)
