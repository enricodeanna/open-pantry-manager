# Open Pantry Manager

> **Generated with AI.** The code of this app was written by Claude, Anthropic's AI model.

A pantry for every place you keep food: the kitchen cupboard, the fridge, the basement freezer. The
app remembers where each food usually goes and where you usually move it, reads shop receipts with
the phone's camera, and puts finished food on your shopping list in
[Open Check Lists](../open-check-lists).

Kotlin Multiplatform with one Compose UI, like Open Check Lists. Only Android is built for now; the
shared code and the screens also compile for desktop, so that desktop, iOS and web can follow.

| Module       | What it is                                                                              |
|--------------|-----------------------------------------------------------------------------------------|
| `shared`     | Pantry model, what the app learns, the text reader and the receipt parser, Open Check Lists' list format and its Nextcloud sync. No UI. |
| `ui`         | The Compose app (`PantryApp`).                                                          |
| `androidApp` | Android shell: the camera, the gallery, and the text reader's networks.                 |
| `ocr`        | `prepare-models.sh`, which makes the text reader's networks.                            |

## Build and run

```sh
./gradlew :shared:jvmTest :ui:jvmTest   # tests, including reading photos of receipts
./gradlew :androidApp:assembleDebug     # APKs in androidApp/build/outputs/apk/debug
./gradlew :androidApp:assembleRelease   # shrunk with R8, unsigned
ocr/prepare-models.sh                   # remakes androidApp/src/main/assets/ocr (needs python3)
```

There is one APK for each kind of processor (`arm64-v8a` for nearly all phones), about 22 MB; its
version code ends in a digit for the kind (1 `armeabi-v7a`, 2 `arm64-v8a`, 3 `x86`, 4 `x86_64`). An app
bundle splits by itself. Nothing in the app is proprietary or from Google Play services, so the same
build serves Google Play and F-Droid.

## How it works

**Spaces and food.** The pantry shows each space as a section that folds, as Open Check Lists shows
sections. Food has a name, a quantity, and optionally an expiry day and the day it was bought (today,
unless you say otherwise, or not known); the same food with the same expiry day in the same space is
counted rather than listed twice, keeping the earlier day of purchase. Each space lists its food by
expiry day, the soonest first, food without a day last. Ticking food means one of it was used: the count
goes down, and the last one takes it out of the pantry. Food expiring within three days, or already
expired, is listed under **Use soon** at the top.

**Voice commands.** The microphone button above *Add food* listens to one sentence, on the phone, and does
what it says, with Undo in the message that follows:

- "ho mangiato una mela", "ho consumato due confezioni di passata", "I drank a bottle of water": that
  many are used (one, unless said); of food of the same name, the one expiring first;
- "ho finito il latte", "I ran out of milk": finished, and offered for the shopping list;
- "ho comprato sei uova nel frigorifero", "I bought a dozen eggs": added where it was said, or where that
  food usually goes. A food the app does not know, nor a common one, opens the *Add food* dialog filled in,
  as does food it cannot tell where to put, so that a mishearing is seen before it is saved;
- "ho spostato il latte in frigorifero", "I moved two bottles of water from the cellar to the pantry":
  moved, all of it unless said; with no space said, the move dialog opens.

Each verb comes in its usual forms, and in other ways of saying it: "ho buttato via", "togli",
"I threw away", "I took" (used); "non c'è più", "manca", "we're out of", "all gone" (finished);
"I picked up", "messo via" (bought); and the app can be spoken to as well as told ("aggiungi un paio di
mele", "sposta il latte"). Numbers go up to a hundred, with "un paio", "una dozzina", "a couple".

Who gives says which way food went: "ho dato", "abbiamo regalato", "gli ho dato", "I gave" is given away
(used); "mi hanno dato", "ci ha regalato", "me le hanno regalate", "mi è stato dato", "they gave me" is
received (bought), and so is "mi hanno portato", while "ho portato" moves. Where several phrases start at
the same word, the longest is taken. "Ho preso" says where from: "ho preso due uova dal frigorifero",
from a space of the pantry, is used; "ho preso il pane" is bought, and opens *Add food* filled in, to be
checked. A shop named ("al supermercato") is not part of the food's name. What the sentence alone cannot
tell, such as "il latte è scaduto" (thrown away, or just past its date?), each household teaches.

**Teaching it words.** When a sentence is not understood because its verb is unknown, the dialog offers
*Teach me*: it shows the words it did not know ("il latte è scaduto": "scaduto"), which can be edited, and
asks what they mean: used some, finished, bought or put in the pantry, or moved. The app learns the phrase,
does the sentence, and from then on understands those words in any sentence and in their other forms
("le mele sono scadute"); they are also told to the recognizer, which hears them better. Taught phrases
are listed in *Settings → Voice commands*, to forget or to add to by hand. They are part of what the app
has learned, so a shared pantry passes them on to every phone sharing it, and a phrase forgotten on one is
forgotten on all. Phrases are kept per language.

Speech is heard by [Vosk](https://alphacephei.com/vosk/) (Apache 2.0), twice over: freely, and listening
only for the words of commands, numbers, common food and the pantry's own food and spaces, which it hears
better ("sei uova" rather than "se vuoi", "two" rather than "to"); the first hearing that names food the
app knows is taken. Listening for known words only, though, the recognizer forces words it does not know
into known ones ("il latte è scaduto" came out as "latte pesca bevuto"), so that hearing counts only when
it is close to the free one (a quarter of the letters at most), and when only it has a verb, the dialog
asks "Did you say …?" before doing anything; *No* offers to teach the words instead. The
sentence is then read by rules, not by an AI model (`shared/.../voice`): a verb says what happened,
numbers how many, prepositions where to and from, and the food is matched as the shopping list's
suggestions are, with plurals ("mela", "mele"). Each language's model, a small Vosk model of 40–50 MB, is
downloaded once, when the microphone is first used in that language, from alphacephei.com, and checked
against its SHA-256; after that nothing said leaves the phone. Voice commands are offered in the language
the app is shown in.

**Undo and redo**, as in Open Check Lists: the arrows at the top take back this phone's latest edits
one by one, and make them again (Ctrl+Z, Ctrl+Y or Ctrl+Shift+Z on a keyboard); a message says what was
undone. That covers food, spaces, receipts put away and what the app learned from each, up to the last
ten edits, while the app is open. Messages about an edit also have an Undo, for that edit while it is
the latest. An undo is an edit of its own, which syncs like any other; in a shared pantry it takes back
only what is still as the edit left it, not what another phone has changed since. The shopping list's
screen has its own undo and redo, for the ticks and additions made there and from the pantry. Search is
in the menu (Ctrl+F), so that the arrows fit beside the title.

**What the app learns** (`shared/.../memory/Memory.kt`), kept on the device in `memory.json`, and shared
with the pantry when it is shared:

- where each food goes. Typing a name offers the foods it knows, and picks the space that food usually
  goes in (marked ★) until you pick another;
- where each food goes when moved from a given space. The move dialog starts on that space; for a food
  never moved from there, on where anything moved from there went;
- how long a food keeps: the days from purchase to expiry, the last five times it was put away with an
  expiry day; the middle one of them (the mean of the middle two) is offered as a one-tap date counted
  from the day of purchase ("As usual: 21 Oct"), so that one odd date does not pull it away. A receipt
  has one day of purchase for all its food; changing it moves the dates the app suggested, not those
  set by hand;
- what each food is called on the shopping list ("Latte" for "Latte p.s. Friuli"), and in which sublist;
- what the lines of receipts mean, and which lines to leave out (bags, deposits), and how many items
  one line is (a pack of 6 bottles). Lines are compared by their letters, so a line read once with a
  space less ("DETERSIVOPIATTI") is still the line it was.

Habits are weights that grow by one each time and fade a little whenever the same food is handled
differently, so a new habit takes over after about three times.

**Receipts.** The camera button opens the receipt's photos: taken with the phone's camera app, or picked
from the gallery. A long receipt is taken in several photos from top to bottom, each showing the last
lines of the one before. Each photo is read as soon as it is added, while the next one is taken, on the
phone: nothing is sent anywhere. Joining the photos, the rows a photo repeats from the one before are
taken once, from whichever photo has them further from its edge (where rows come out cut); rows that
read a little differently in the two still match.

The reader (`shared/.../ocr`) is PaddleOCR's PP-OCRv6 "small": one network finds the lines of text,
another reads them. The steps around the networks, from PaddleOCR and RapidOCR, are written in Kotlin,
so that desktop and iOS can use them; ONNX Runtime runs the networks. `ReceiptParser` then puts the
description and price columns back into rows (undoing the photo's tilt), keeps the product rows, and
drops headers, totals, VAT, weights and discounts; "2 x 0,89" rows give the quantity of the product
whose price they add up to. It knows Italian receipts best.

Each line is then *known* (you said what it is before), *probable* (it reads almost like a known line,
or names a known food: "LATTE P.S. 1L" is Latte), *unknown* (the name is tidied up from the receipt:
"PROSC.CRUDO S.DANIELE" becomes "Prosc crudo daniele"), or *left out as last time*. The review screen
lists the unsure ones first and asks what they are and where they go; everything can be changed before
saving. Saving puts the food away and learns from every line. A food can go in a space the pantry does not
have yet: **New space** among the spaces names one there and then.

**The shopping list** is a list of Open Check Lists, kept in the same file by both apps. In Open Check
Lists, share the list through Nextcloud, copy its link, and paste it in this app (Settings, or the
cart icon). When food is finished, the app offers to put it on that list, under the name it had there
last time (which can be changed), in one of its sublists (Open Check Lists' sections), none, or a new
one; it suggests the sublist it went on last time. Items of the list with the same or a similar name
("Latte" for "Latte intero", "Mele" for "Mela"), ticked or not, are offered instead: picking one puts it
back on the list, and from then on that is the food's name there. A name already on the list, unticked,
is not added again. The cart icon shows the list, to tick while shopping.

The list file is read and written exactly as Open Check Lists does: the same fields, the same
per-item merge where the latest change wins, the same conditional writes on Nextcloud. The code for it
(`shared/.../checklist` and `shared/.../sync`) is copied from Open Check Lists; a change to its file
format must be made in both apps. A file saved by a newer format is refused, not damaged.

**Sharing the pantry.** Menu → *Share the pantry* (or Settings): share a Nextcloud folder by link with
editing allowed, and paste the link. The pantry is saved there as `Dispensa.pantry` (`Pantry.pantry` in
English), with everything the app has learned and the link of the shopping list. On another phone,
paste the same link (Settings shows the address to send, which names the file): that phone joins the
pantry, and follows its shopping list. A phone with spaces or food of its own is asked whether to add
them to the shared pantry, where spaces with the same name become one and what both phones learned adds
up, or to use only the shared pantry, keeping its own aside in `pantry-before-sharing-<time>.json`.

From then on every phone syncs shortly after each change and when the app is opened, as the shopping
list does: each space, item and learned thing (a food, where things go from a space, a receipt line)
carries when and on which phone it last changed, the latest change wins, and writes are conditional, so
no change is overwritten unseen. Two phones learning about the same food within the same few seconds
keep one of the two lessons. An undo takes back only what its change did, not what other phones did
meanwhile. Linking or unlinking the shopping list on one phone does so on all of them. Theme, language
and folded spaces stay each phone's own.

From format 2 (version 0.4.0) items carry the day they were bought, and from format 3 (0.6.0) the memory
carries the phrases taught to voice commands: a phone with an older version refuses the file, asking to
be updated, rather than dropping them.

The file ends in `.pantry`, not `.json`, and is marked as a pantry inside: Open Check Lists takes every
`.json` file in a shared folder for a list, and an empty pantry reads as an empty list there. Each app
refuses the other's files, so a pantry and its shopping list can live in the same folder.

## Adding a language

The app speaks English and Italian, and its voice commands follow its languages. A new language takes:

- its texts: a `Strings` object in `ui/.../Strings.kt` (with its `languageCode`, the ISO 639-1 code) and a
  `Messages` object in `shared/.../Messages.kt`, and an entry in `LanguageChoice` and the settings;
- its voice commands: a `VoiceLanguage` in `shared/.../voice/`, like `Italian.kt`: the verbs and phrases
  for using, finishing, adding and moving food, numbers, articles, containers, prepositions, how plurals
  are made, a list of common food, a few examples, the words as the recognizer writes them where accents
  or apostrophes differ ("più", "we're"), and the small Vosk model of the language from
  https://alphacephei.com/vosk/models (its address, SHA-256 and size), added to `VoiceLanguages.all`. The
  tests check that every language of the app has one. Short food words that sound like numbers are best
  left out of the common food: with "tè" listed, "tre mele" was heard as "tè mele". Users teach the rest.

Receipts are read in Latin script only (see below).

## Storage

Everything is in the app's files directory, as JSON: `pantry.json` (spaces and food, stamped per change
and merged like an Open Check Lists list), `memory.json` (with a stamp per learned thing),
`settings.json`, `device.json` (this device's id, the linked list and the shared pantry) and
`shopping-list.json` (this device's copy of the list). The shared pantry's file holds `pantry.json` and
`memory.json` together (`sync/SharedPantry.kt`). A `pantry.json` this version cannot read is kept
aside as `pantry-unreadable-<time>.json` instead of being overwritten.

## The text reader's networks

`ocr/prepare-models.sh` fetches PP-OCRv6 small as RapidOCR publishes it in ONNX form (Apache 2.0),
checks it against known hashes, and prepares it for phones:

- the recognizer is cut down to the 371 characters of Latin script, digits, punctuation and currency
  signs, out of 18 708 (most of them Chinese and Japanese): a fifth of its size and much faster;
- the weights are stored at half precision and turned back to single precision as the networks load.
  Networks converted to half precision throughout read nothing on ARM, which computes in it;
- the characters go in `characters.txt`: ONNX Runtime's Java API garbles some of them when they are
  left in the model.

ONNX Runtime stays at **1.28.0**: from 1.29.0 its Android library carries Microsoft's telemetry client,
started with the app by a content provider of its own, and asks for network access. Building it from
source without that would allow newer versions, and a smaller library with only the operators these
networks use.

On a moto g 5G (release build) a photo is read in about 3 seconds, a 1500 × 2000 photo of a whole long
receipt in about 6. Two threads are faster there than four.

## Testing on a phone

Debug builds take three extras, to be driven over adb:

```sh
adb shell am start -n eu.studiodeanna.openpantrymanager/.android.MainActivity \
    --es receiptImage /data/data/eu.studiodeanna.openpantrymanager/files/test/long-1.jpg,/data/…/long-2.jpg \
    --es voiceAudio /data/data/eu.studiodeanna.openpantrymanager/files/test/sentence.wav \
    --ez overLockScreen true
```

`receiptImage` makes the next photos "taken" these pictures, one per tap, instead of the camera's (copy
them there with `adb push` to `/data/local/tmp`, then `adb shell run-as eu.studiodeanna.openpantrymanager
cp …`). `voiceAudio` makes the next sentence "heard" that WAV file (16 kHz, mono, 16 bits) instead of the
microphone. `overLockScreen` shows the app over the lock screen of a test phone. Debug builds also log
what was read from each photo, row by row, with timings, under the `Receipt` tag, and each sentence as
heard both ways under `Voice`.

The tests read made-up receipts printed by a computer and photographed by `magick`: tilted, blurred,
shaded, small, and a long one in three overlapping photos and in one large photo
(`shared/src/jvmTest/resources/receipts`).

## Known limits

- **Tested** on one phone (moto g 5G, Android 11) over adb: spaces, adding with suggestions, moving,
  finishing, receipts (photos read, joined, reviewed, saved, then read again as known), and the
  shopping list against a real Nextcloud 34 folder share, both ways, including an R8 release build.
  The receipts were made-up ones; not yet tried with a real paper receipt, nor with the phone's camera
  app and gallery (which do not open over a locked phone's lock screen), nor next to Open Check Lists
  on the same list.
- **Sharing the pantry** was tested with two simulated phones and against a real Nextcloud 34 folder share
  (`RealNextcloudTest`, run with `OPM_NEXTCLOUD_TEST_LINK` set to a folder link), not yet with two
  real phones.
- **Voice commands** were tried with recorded synthetic speech, on the desktop and on an Android emulator
  (download, recognition, teaching a phrase, checking what was heard, the release build), not yet with a
  real voice through a phone's microphone. The phrases built in are a first set; what a household says
  differently it teaches.
- **Google Drive is not supported yet**, for lists or pantries: only Nextcloud links. Drive would need
  this app's own OAuth client in the Google Cloud project, as Open Check Lists has.
- The shared pantry's file carries the shopping list's link, with its password if the share has one:
  whoever can open the pantry can open the list.
- The shopping list is reached through its share link, so a list that lives only in Open Check Lists on
  the same phone, unshared, cannot be used.
- Receipts are read in **Latin script** only.
- Two of a food rung up one by one print the same row twice; if a photo ends between the two, the second
  can be taken for a repeat and left out. The review shows the count, to put right.
- The reader sometimes runs words together ("DETERSIVOPIATTI"), and on very small print takes a letter for
  a digit ("B10"); the review is there for both, and the app learns the line as read.

## License

Open Pantry Manager is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version. See [LICENSE](LICENSE).

Copyright © 2026 HTF
