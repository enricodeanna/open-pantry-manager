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
sections. Food has a name, a quantity, and optionally an expiry day; the same food with the same day in
the same space is counted rather than listed twice. Ticking food means one of it was used: the count
goes down, and the last one takes it out of the pantry. Food expiring within three days, or already
expired, is listed under **Use soon** at the top.

**What the app learns** (`shared/.../memory/Memory.kt`), kept on the device in `memory.json`:

- where each food goes. Typing a name offers the foods it knows, and picks the space that food usually
  goes in (marked ★) until you pick another;
- where each food goes when moved from a given space. The move dialog starts on that space; for a food
  never moved from there, on where anything moved from there went;
- how long a food keeps, from the expiry days you set, offered as a one-tap date ("As usual: 21 Oct");
- which sublist of the shopping list each food goes on;
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
cart icon). When food is finished, the app offers to put it on that list, in one of its sublists
(Open Check Lists' sections), none, or a new one; it suggests the sublist it went on last time. A food
already unticked on the list is not added again; a ticked one is unticked rather than added twice.
The cart icon shows the list, to tick while shopping.

The list file is read and written exactly as Open Check Lists does: the same fields, the same
per-item merge where the latest change wins, the same conditional writes on Nextcloud. The code for it
(`shared/.../checklist` and `shared/.../sync`) is copied from Open Check Lists; a change to its file
format must be made in both apps. A file saved by a newer format is refused, not damaged.

## Storage

Everything is in the app's files directory, as JSON: `pantry.json` (spaces and food, stamped per change
and merged like an Open Check Lists list, so that the pantry itself can later be shared between
devices), `memory.json`, `settings.json`, `device.json` (this device's id and the linked list) and
`shopping-list.json` (this device's copy of the list). A `pantry.json` this version cannot read is kept
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

Debug builds take two extras, to be driven over adb:

```sh
adb shell am start -n eu.studiodeanna.openpantrymanager/.android.MainActivity \
    --es receiptImage /data/data/eu.studiodeanna.openpantrymanager/files/test/long-1.jpg,/data/…/long-2.jpg \
    --ez overLockScreen true
```

`receiptImage` makes the next photos "taken" these pictures, one per tap, instead of the camera's (copy
them there with `adb push` to `/data/local/tmp`, then `adb shell run-as eu.studiodeanna.openpantrymanager
cp …`). `overLockScreen` shows the app over the lock screen of a test phone. Debug builds also log what
was read from each photo, row by row, with timings, under the `Receipt` tag.

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
- **Google Drive lists are not supported yet**: only Nextcloud links. Drive would need this app's own
  OAuth client in the Google Cloud project, as Open Check Lists has.
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
