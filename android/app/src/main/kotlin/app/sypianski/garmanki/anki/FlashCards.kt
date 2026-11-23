package app.sypianski.garmanki.anki

import android.net.Uri

/**
 * Constants re-declared from the FlashCardsContract to avoid a
 * library dependency. Literals verified against AnkiQuest's AnkiDroid fork:
 * https://github.com/AnkiQuest/AnkiQuest-Android/blob/master/api/src/main/java/com/ichi2/anki/FlashCardsContract.kt
 * (fork commit bd339154fb535bfd386f0811711f9fae07c8de0a, 2026-11-18).
 */
object FlashCards {
    /** Same action in AnkiDroid and AnkiQuest; sent to the chosen app's package. */
    const val SYNC_ACTION = "com.ichi2.anki.DO_SYNC"

    /** Authority, package and permission vary per app: see [FlashcardApp]. */
    fun decksUri(app: FlashcardApp): Uri = uri(app, "decks")
    fun scheduleUri(app: FlashcardApp): Uri = uri(app, "schedule")
    fun notesUri(app: FlashcardApp): Uri = uri(app, "notes")

    private fun uri(app: FlashcardApp, path: String): Uri =
        Uri.withAppendedPath(Uri.parse("content://${app.authority}"), path)

    object Deck {
        const val DECK_NAME = "deck_name"
        const val DECK_ID = "deck_id"

        /** JSON array, documented order: `[learn, review, new]`. */
        const val DECK_COUNTS = "deck_count"
        const val DECK_DYN = "deck_dyn"
    }

    object ReviewInfo {
        const val NOTE_ID = "note_id"
        const val CARD_ORD = "ord"
        const val BUTTON_COUNT = "button_count"

        /** JSON array of per-button interval labels, e.g. `["<1 min","10 min","3 d","4 d"]`. */
        const val NEXT_REVIEW_TIMES = "next_review_times"

        /** Answer ease for update(): 1..4 = Again/Hard/Good/Easy (com.ichi2.anki.api.Ease). */
        const val EASE = "answer_ease"
        const val TIME_TAKEN = "time_taken"
        const val BURY = "buried"
        const val SUSPEND = "suspended"
    }

    object Card {
        const val CARD_ORD = "ord"

        /** Rendered question HTML without CSS/JS. */
        const val QUESTION_SIMPLE = "question_simple"

        /** Rendered answer HTML without the question part and without CSS/JS. */
        const val ANSWER_PURE = "answer_pure"
    }

    object Note {
        const val ID = "_id"

        /** Space-separated tag list. */
        const val TAGS = "tags"
    }
}
