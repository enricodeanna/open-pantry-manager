package eu.studiodeanna.openpantrymanager.voice

import eu.studiodeanna.openpantrymanager.costs.Measure
import eu.studiodeanna.openpantrymanager.memory.Memory
import eu.studiodeanna.openpantrymanager.memory.phrasesIn
import eu.studiodeanna.openpantrymanager.memory.placeFor
import eu.studiodeanna.openpantrymanager.model.PantryDocument
import eu.studiodeanna.openpantrymanager.model.PantryItem
import eu.studiodeanna.openpantrymanager.model.Space
import eu.studiodeanna.openpantrymanager.model.editDistance
import eu.studiodeanna.openpantrymanager.model.liveItems
import eu.studiodeanna.openpantrymanager.model.nameKey
import eu.studiodeanna.openpantrymanager.model.visibleSpaces
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a sentence said to the app asks it to do. */
sealed interface VoiceCommand {
    /**
     * [count] of [item] were used up: eaten, drunk, cooked. All of it, when [count] is its quantity or
     * more. With an [amount] ("100 grammi"), that much was taken from an opened box instead.
     */
    data class Use(val item: PantryItem, val count: Int, val amount: Measure? = null) : VoiceCommand

    /** [item] is finished, all of it. */
    data class Finish(val item: PantryItem) : VoiceCommand

    /** [count] closed boxes of [item] were opened. */
    data class Open(val item: PantryItem, val count: Int) : VoiceCommand

    /**
     * [count] of the food [name] were bought, to go in [space]; null when the app cannot tell where.
     * [sure]: the name is of food the pantry knows, or common food, rather than maybe a mishearing, and
     * the verb said it was bought, not maybe taken out ("ho preso il pane").
     */
    data class Add(val name: String, val count: Int, val space: String?, val sure: Boolean = true) : VoiceCommand

    /** [count] of [item] were moved to [space]; null when it was not said where, or the space is not known. */
    data class Move(val item: PantryItem, val count: Int, val space: String?) : VoiceCommand

    /** Nothing the app could do: no verb it knows, or no food of the pantry. */
    data class NotUnderstood(val heard: String) : VoiceCommand

    /**
     * [command], understood only from [heard], the sentence as heard when listening for words of
     * commands alone, while heard freely it had no verb: the verb may be a word not known forced into
     * one known, so it is done only once the user says [heard] is what they said.
     */
    data class Check(val command: VoiceCommand, val heard: String) : VoiceCommand
}

/** What the verb of a voice command says was done; also what a phrase the user taught the app means. */
@Serializable
enum class VoiceAction {
    /** Some of a food was used: eaten, drunk, cooked, thrown away. */
    @SerialName("use") Use,
    /** A food is finished, all of it. */
    @SerialName("finish") Finish,
    /** Food was bought, or put in the pantry. */
    @SerialName("add") Add,
    /** Food was moved from one space to another. */
    @SerialName("move") Move,
    /** A box of a food was opened. */
    @SerialName("open") Open,
}

/**
 * What was heard asks to do, when it was heard in more ways than one ([heard]: freely first, then as
 * words of commands and food): the first that makes a command about food the pantry has, or food it
 * knows or common food; else the first command at all, or that none was understood.
 *
 * Listening for words it knows, the recognizer hears those better, but forces words it does not know
 * into them: "il latte è scaduto" comes out as "latte pesca bevuto". So another hearing counts only
 * near the free one, and when only it has a verb, what it says is to be checked with the user first.
 */
fun understandVoice(heard: List<String>, language: VoiceLanguage, pantry: PantryDocument, memory: Memory): VoiceCommand {
    val free = heard.firstOrNull().orEmpty()
    val hearings = heard.filterIndexed { i, it -> it.isNotBlank() && (i == 0 || near(free, it)) }
    val commands = hearings.map { understandVoice(it, language, pantry, memory) }
    val chosen = commands.firstOrNull { it !is VoiceCommand.NotUnderstood && (it !is VoiceCommand.Add || it.sure) }
        ?: commands.firstOrNull { it !is VoiceCommand.NotUnderstood }
        ?: return commands.firstOrNull() ?: VoiceCommand.NotUnderstood(heard.firstOrNull { it.isNotBlank() }.orEmpty())
    // Of hearings doing the same to the same food, the last, of words alone, hears numbers better ("two", not "to").
    val best = commands.indexOfLast { it::class == chosen::class && it.food() == chosen.food() }
    val freeHasVerb = free.isNotBlank() && findVerb(words(free), language, memory.phrasesIn(language.code), BooleanArray(words(free).size)) != null
    return if (hearings[best] == free || freeHasVerb) commands[best] else VoiceCommand.Check(commands[best], hearings[best])
}

/** The words of a sentence as compared: [nameKey]s. */
private fun words(sentence: String): List<String> =
    sentence.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.map(::nameKey)

/** The food a command is about: the item, or the name of food bought. */
private fun VoiceCommand.food(): String? = when (this) {
    is VoiceCommand.Use -> item.id
    is VoiceCommand.Finish -> item.id
    is VoiceCommand.Open -> item.id
    is VoiceCommand.Move -> item.id
    is VoiceCommand.Add -> nameKey(name)
    is VoiceCommand.NotUnderstood, is VoiceCommand.Check -> null
}

/** Whether two hearings of a sentence differ in few of their letters, as a word or two misheard do. */
private fun near(a: String, b: String): Boolean {
    val x = nameKey(a).replace(" ", "")
    val y = nameKey(b).replace(" ", "")
    return editDistance(x, y) <= maxOf(x.length, y.length) * NEAR
}

/**
 * How many of their letters two hearings of a sentence may differ in: in tests, words misheard made
 * a tenth or so ("se vuoi", "sei uova"); words forced into others, more than a third.
 */
private const val NEAR = 0.25

/**
 * The words the recognizer is told to listen for, besides listening freely: those of commands in
 * [language], built in and taught, of common food, and of the food and spaces of [pantry] and the food
 * [memory] knows.
 */
fun voiceVocabulary(language: VoiceLanguage, pantry: PantryDocument, memory: Memory): List<String> {
    val names = pantry.visibleSpaces().map { it.name } + pantry.liveItems().map { it.name } + memory.foods.values.map { it.name }
    // Phrases taught are kept as written, apostrophes too ("c'è").
    val words = language.use + language.finish + language.add + language.move + language.take + language.open + language.numbers.keys +
        language.units.keys + language.all +
        language.fillers + language.containers + language.into + language.from + language.foods + language.spoken +
        memory.phrasesIn(language.code).keys
    return (words.flatMap { it.split(' ') } + names.flatMap { name -> name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")) })
        .filter { it.isNotEmpty() }
        .distinct()
}

/**
 * What [heard], a sentence in [language], asks to do with the pantry [pantry]: which food, how many,
 * from and to which space. A verb of the language says what happened; numbers say how many (one,
 * unless said; all of it, to move); the food is the pantry's food named most nearly, whatever the order
 * of the words and the plural, and of food of the same name, an opened box first (a closed one, to
 * open), then the one expiring soonest, as the one eaten first. Food in boxes is used as a tap uses it:
 * used, with no number said, a closed box is opened, an opened one taken out. What was bought may be
 * food the pantry has never had: its name is what was said, and it goes where [memory] says it usually
 * goes, or where it was said to go.
 */
fun understandVoice(heard: String, language: VoiceLanguage, pantry: PantryDocument, memory: Memory): VoiceCommand {
    val notUnderstood = VoiceCommand.NotUnderstood(heard.trim())
    // The words as said (accents kept, for the name of new food), and as compared.
    val said = heard.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    val words = said.map(::nameKey)
    if (words.isEmpty()) return notUnderstood
    val taken = BooleanArray(words.size)

    val found = findVerb(words, language, memory.phrasesIn(language.code), taken) ?: return notUnderstood
    val spaces = pantry.visibleSpaces()
    val (to, from) = findSpaces(words, language, spaces, taken, withoutPreposition = !found.take && found.action != VoiceAction.Use && found.action != VoiceAction.Finish)
    // Taken from a space of the pantry, food was used; taken elsewhere, it was bought.
    val verb = if (found.take && from != null) VoiceAction.Use else found.action
    var count: Int? = null
    var amount: Measure? = null
    var all = false
    var tens: Int? = null
    for (i in words.indices) {
        if (taken[i]) continue
        var number = words[i].toIntOrNull() ?: language.numbers[words[i]]
        // "Twenty four", as the recognizer writes it, is one number.
        if (number != null && number < 10 && tens != null && taken.getOrNull(i - 1) == true) number += tens
        tens = number?.takeIf { it in 20..90 && it % 10 == 0 }
        // A number with a unit after it is an amount: "100 grammi", "un etto".
        val unit = words.getOrNull(i + 1)?.let { language.units[it] }
        when {
            number != null && unit != null -> {
                amount = Measure(unit.value * number, unit.unit)
                taken[i + 1] = true
            }
            number != null -> count = maxOf(count ?: 0, number)
            words[i] in language.all -> all = true
            else -> continue
        }
        taken[i] = true
    }
    // What is left names the food.
    val ignored = language.fillers + language.containers + language.into + language.from + language.units.keys
    val food = words.indices.filter { !taken[it] && words[it] !in ignored }
    if (food.isEmpty()) return notUnderstood
    val foodWords = food.map { words[it] }

    val live = spaces.mapTo(HashSet()) { it.id }
    val items = pantry.liveItems().filter { it.space in live }
    return when (verb) {
        VoiceAction.Add -> {
            val known = (items.map { it.name } + memory.foods.values.map { it.name })
                .distinctBy(::nameKey)
                .filter { sameFood(it, foodWords, language) }
                .maxByOrNull { score(it, foodWords, language) }
            // A new food is called as it was said, from its first word to its last.
            val name = known ?: said.subList(food.first(), food.last() + 1).joinToString(" ").replaceFirstChar { it.uppercase() }
            val space = to ?: memory.placeFor(name, live)
            val common = language.foods.mapTo(HashSet(), ::nameKey)
            val sure = !found.take && (known != null || meaningful(name, language).all { word -> common.any { sameWord(it, word, language) } })
            VoiceCommand.Add(name, count ?: 1, space, sure)
        }
        else -> {
            val candidates = items.filter {
                (from == null || it.space == from) && (verb != VoiceAction.Move || it.space != to) && (verb != VoiceAction.Open || it.opened == null)
            }
            val item = candidates
                .filter { score(it.name, foodWords, language) > 0 }
                .sortedWith(
                    compareByDescending<PantryItem> { score(it.name, foodWords, language) }
                        // Used or finished, the box already open; a closed one is opened (candidates above).
                        .thenBy { (verb == VoiceAction.Use || verb == VoiceAction.Finish) && it.opened == null }
                        .thenBy { it.expires == null }
                        .thenBy { it.expires },
                )
                .firstOrNull() ?: return notUnderstood
            when (verb) {
                VoiceAction.Finish -> VoiceCommand.Finish(item)
                VoiceAction.Open -> VoiceCommand.Open(item, if (all) item.quantity else count ?: 1)
                VoiceAction.Use -> when {
                    all -> VoiceCommand.Finish(item)
                    amount != null && item.size != null -> VoiceCommand.Use(item, 1, amount)
                    count == null && item.boxed && item.opened == null -> VoiceCommand.Open(item, 1)
                    else -> VoiceCommand.Use(item, count ?: 1)
                }
                else -> VoiceCommand.Move(item, if (all || count == null) item.quantity else count, to)
            }
        }
    }
}

/** What the verb of a sentence says was done; [take], taken: used if from a space of the pantry, else bought. */
private data class Verb(val action: VoiceAction, val take: Boolean = false)

/**
 * The verb of the sentence, the first one said, as a phrase the user taught ([learned]) or one of the
 * language; where several start at the same word, a phrase taught first, and the longest ("mi hanno
 * dato", received, not "hanno dato", given away). Marks its words [taken]. A phrase taught is heard in
 * any of its forms ("regalato" is also "regalate").
 */
private fun findVerb(words: List<String>, language: VoiceLanguage, learned: Map<String, VoiceAction>, taken: BooleanArray): Verb? {
    val taught = learned.map { (phrase, action) -> nameKey(phrase).split(' ').filter { it.isNotEmpty() } to Verb(action) }
        .filter { it.first.isNotEmpty() }
        .sortedByDescending { it.first.size }
    val verbs = listOf(
        Verb(VoiceAction.Finish) to language.finish,
        Verb(VoiceAction.Move) to language.move,
        Verb(VoiceAction.Add) to language.add,
        Verb(VoiceAction.Use) to language.use,
        Verb(VoiceAction.Add, take = true) to language.take,
        Verb(VoiceAction.Open) to language.open,
    )
    val longest = verbs.maxOf { (_, forms) -> forms.maxOfOrNull { it.count { c -> c == ' ' } + 1 } ?: 1 }
    for (start in words.indices) {
        fun found(length: Int, verb: Verb): Verb {
            for (i in start until start + length) taken[i] = true
            return verb
        }
        for ((phrase, verb) in taught) {
            if (start + phrase.size > words.size) continue
            val said = phrase.indices.all { words[start + it] == phrase[it] || language.stem(words[start + it]) == language.stem(phrase[it]) }
            if (said) return found(phrase.size, verb)
        }
        for (length in minOf(longest, words.size - start) downTo 1) {
            val phrase = words.subList(start, start + length).joinToString(" ")
            verbs.firstOrNull { (_, forms) -> phrase in forms }?.let { return found(length, it.first) }
        }
    }
    return null
}

/**
 * The words of [heard] that may say what was done in a way the app does not know yet, for the user to
 * teach it: the first run of words that are not food, numbers, spaces nor small words, as said. Null
 * when the sentence has a verb the app knows already (then it was the food it could not tell), or has
 * no such words.
 */
fun phraseToLearn(heard: String, language: VoiceLanguage, pantry: PantryDocument, memory: Memory): String? {
    val said = heard.lowercase().split(Regex("[^\\p{L}\\p{N}']+")).map { it.trim('\'') }.filter { it.isNotEmpty() }
    val keys = said.map { nameKey(it).split(' ').filter(String::isNotEmpty) }
    if (findVerb(keys.flatten(), language, memory.phrasesIn(language.code), BooleanArray(keys.sumOf { it.size })) != null) return null

    // Small words may sit inside a phrase ("buttato via", "run out of"); numbers and food end it.
    val glue = language.fillers + language.into + language.from
    val small = glue + language.containers + language.all + language.numbers.keys
    val named = (pantry.visibleSpaces().map { it.name } + pantry.liveItems().map { it.name } + memory.foods.values.map { it.name } + language.foods)
        .flatMap { meaningful(it, language) }
        .toSet()
    fun isKnown(i: Int) = keys[i].all { word -> word in small || word.toIntOrNull() != null || named.any { sameWord(it, word, language) } }

    val start = said.indices.firstOrNull { !isKnown(it) } ?: return null
    var end = start
    var i = start + 1
    while (i < said.size && (!isKnown(i) || keys[i].all { it in glue })) {
        if (!isKnown(i)) end = i
        i++
    }
    return said.subList(start, end + 1).joinToString(" ")
}

/**
 * The spaces the sentence names after a preposition: where food goes, and where it comes from. Words
 * after a preposition that name no space are part of the food ("pasta al pomodoro"). [withoutPreposition],
 * a space named without one, as the recognizer sometimes drops it, is where food goes.
 */
private fun findSpaces(
    words: List<String>,
    language: VoiceLanguage,
    spaces: List<Space>,
    taken: BooleanArray,
    withoutPreposition: Boolean,
): Pair<String?, String?> {
    var to: String? = null
    var from: String? = null
    fun spaceAt(start: Int): Pair<Space, Int>? = spaces.mapNotNull { space ->
        val name = meaningful(space.name, language)
        if (name.isEmpty() || start + name.size > words.size) return@mapNotNull null
        // Articles may follow the preposition: "in the fridge".
        var at = start
        while (at < words.size && words[at] in language.fillers) at++
        if (at + name.size > words.size) return@mapNotNull null
        val matched = name.indices.all { sameWord(name[it], words[at + it], language) }
        if (matched) space to (at + name.size) else null
    }.maxByOrNull { it.second }

    for (i in words.indices) {
        if (taken[i]) continue
        val isInto = words[i] in language.into
        val isFrom = words[i] in language.from
        if (!isInto && !isFrom) continue
        val (space, end) = spaceAt(i + 1) ?: continue
        for (j in i until end) taken[j] = true
        if (isInto) to = space.id else from = space.id
    }
    if (to == null && withoutPreposition) {
        for (i in words.indices) {
            if (taken[i]) continue
            val (space, end) = spaceAt(i) ?: continue
            if (space.id == from) continue
            for (j in i until end) taken[j] = true
            to = space.id
            break
        }
    }
    return to to from
}

/** The words of a name that tell the food: not articles, prepositions nor containers. */
private fun meaningful(name: String, language: VoiceLanguage): List<String> {
    val words = nameKey(name).split(' ').filter { it.isNotEmpty() }
    return words.filter { it !in language.fillers && it !in language.into && it !in language.from && it !in language.containers }
        .ifEmpty { words }
}

/** How nearly [said] names the food [name]: two for each of its words said as it is, one for each said nearly. */
private fun score(name: String, said: List<String>, language: VoiceLanguage): Int =
    meaningful(name, language).sumOf { word -> said.maxOfOrNull { wordScore(word, it, language) } ?: 0 }

/** Whether [said] is the food [name], every word of it and nothing else: so a new food is not taken for a known one. */
private fun sameFood(name: String, said: List<String>, language: VoiceLanguage): Boolean {
    val words = meaningful(name, language)
    return words.all { word -> said.any { sameWord(word, it, language) } } && said.all { word -> words.any { sameWord(word, it, language) } }
}

private fun sameWord(a: String, b: String, language: VoiceLanguage) = wordScore(a, b, language) > 0

private fun wordScore(a: String, b: String, language: VoiceLanguage): Int = when {
    a == b || language.stem(a) == language.stem(b) -> 2
    minOf(a.length, b.length) < 4 -> 0
    a.startsWith(b) || b.startsWith(a) -> 1
    a[0] == b[0] && editDistance(a, b) <= 1 -> 1
    else -> 0
}
