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
        "consume", "consumes", "had", "cooked", "cook", "cooks", "took", "take", "takes", "removed", "remove", "removes",
        "threw away", "thrown away", "throw away", "threw out", "thrown out", "throw out", "tossed", "toss", "binned",
        "gave away", "given away", "give away",
        // Given away by the speaker: "I gave"; given to the speaker is under add.
        "i gave", "we gave", "i ve given", "we ve given", "i have given", "we have given",
    ),
    finish = setOf(
        "finished", "finish", "finishes", "emptied", "ran out", "run out", "used up", "use up", "out of", "gone",
        "all gone", "no more", "none left", "nothing left", "deleted", "delete",
    ),
    add = setOf(
        "bought", "buy", "buys", "added", "add", "adds", "put", "puts", "got", "get", "purchased", "stocked", "picked up",
        "pick up", "received", "receive", "restocked", "stocked up", "stored", "store", "put away", "brought home",
        "bring home",
        // "They gave me some apples": given, not given away.
        "gave me", "gave us", "given me", "given us", "was given", "were given", "been given",
    ),
    move = setOf("moved", "move", "moves", "transferred", "transfer", "shifted", "shift"),
    numbers = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "dozen" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "hundred" to 100, "couple" to 2, "pair" to 2,
    ),
    all = setOf("all", "everything"),
    fillers = setOf(
        "i", "we", "you", "have", "has", "just", "also", "already", "the", "of", "some", "my", "our", "it", "them", "me",
        "they", "he", "she", "us", "him", "her", "their", "your", "is", "are", "was", "were", "been", "be", "there", "any",
        "more", "another", "today", "yesterday", "this", "that", "these", "those", "please", "now", "and", "so", "too",
        "again", "at",
        // Where it was bought is not part of its name: "milk at the supermarket".
        "supermarket", "shop", "market",
        // What is left of "we're", "I've", "it's" and the like.
        "re", "ve", "s", "ll", "d", "m",
    ),
    containers = setOf(
        "box", "boxes", "pack", "packs", "packet", "packets", "bottle", "bottles", "can", "cans", "jar", "jars", "bag",
        "bags", "piece", "pieces", "carton", "cartons", "tin", "tins", "tub", "tubs", "slice", "slices", "loaf",
        "loaves", "bar", "bars", "cup", "cups", "glass", "glasses", "pound", "pounds", "kilo", "kilos", "liter",
        "liters", "litre", "litres", "bunch", "bunches", "head", "heads", "pot", "pots", "tube", "tubes",
        "sack", "sacks", "package", "packages", "portion", "portions", "serving", "servings", "gram", "grams", "ounce",
        "ounces", "gallon", "gallons", "pint", "pints", "clove", "cloves", "tray", "trays",
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
            "sparkling wine beer juice soda ice cream frozen spaghetti noodles pizza pie cake muffins " +
            // Singulars, for the recognizer to hear them too.
            "strawberry grape cherry plum mushroom pea bean lentil cookie biscuit cracker muffin " +
            "asparagus artichokes arugula rocket leeks cucumber cucumbers olives capers walnuts almonds hazelnuts " +
            "pistachios peanuts nuts raisins dates figs blueberries raspberries blackberries berries tangerines " +
            "clementines grapefruit lime limes avocado avocados mango coconut kale tofu hummus polenta barley quinoa " +
            "couscous oatmeal granola breadcrumbs ricotta feta cheddar brie gouda hot dogs hamburgers burgers meatballs " +
            "steak veal lamb rabbit trout squid mussels clams octopus mackerel sardines bagels rolls buns toast tortillas " +
            "wraps croissants pudding candy chips crisps popcorn cola lemonade tonic chamomile soup syrup peanut " +
            "oregano basil parsley rosemary sage cinnamon vanilla saffron curry paprika chili margarine " +
            // What else a pantry keeps.
            "detergent soap toothpaste shampoo toilet paper napkins tissues sponges bleach softener diapers"
        ).split(" ").toSet(),
    examples = listOf("I ate an apple", "I bought six eggs", "I moved the milk to the fridge", "I finished the pasta"),
    spoken = setOf(
        "we're", "they're", "you're", "i've", "we've", "they've", "you've", "it's", "there's", "that's", "i'm", "i'd",
        "we'd", "i'll", "we'll",
    ),
)
