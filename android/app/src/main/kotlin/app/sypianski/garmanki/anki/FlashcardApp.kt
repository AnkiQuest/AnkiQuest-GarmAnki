package app.sypianski.garmanki.anki

/**
 * Closed list of apps that serve AnkiDroid's FlashCardsContract. Garmanki
 * never talks to any other app that declares a provider. AnkiQuest is an
 * AnkiDroid fork with the same provider code under its own identity.
 */
enum class FlashcardApp(
    val packageName: String,
    val authority: String,
    val permission: String,
) {
    ANKIDROID(
        "com.ichi2.anki",
        "com.ichi2.anki.flashcards",
        "com.ichi2.anki.permission.READ_WRITE_DATABASE",
    ),
    ANKIQUEST(
        "org.ankiquest.anki",
        "org.ankiquest.anki.flashcards",
        "org.ankiquest.anki.permission.READ_WRITE_DATABASE",
    ),
    ;

    companion object {
        /** Used without asking when installed; every other app is opt-in. */
        val DEFAULT = ANKIDROID
    }
}

/** Platform seam: what PackageManager says about installed apps. */
interface InstalledApps {
    /** SHA-256 hex digests of the package's signing certificates; empty if not installed. */
    fun signerDigests(packageName: String): Set<String>

    /** Package that currently holds the provider authority, or null. */
    fun authorityHolder(authority: String): String?
}

/** What the user (or trust on first use) last chose. */
sealed interface StoredChoice {
    /** Nothing chosen yet: the default is used if installed. */
    data object None : StoredChoice

    /** App chosen, bound to its package and signing certificate. */
    data class Pinned(val app: FlashcardApp, val packageName: String, val digest: String) : StoredChoice

    /** A pin stopped matching: the user must choose again. */
    data object Revoked : StoredChoice
}

/** Persistence seam for the choice. */
interface PinStore {
    fun load(): StoredChoice
    fun save(choice: StoredChoice)
}

sealed interface FlashcardAppState {
    data object NoneInstalled : FlashcardAppState
    data class NeedsChoice(val candidates: List<FlashcardApp>) : FlashcardAppState
    data class Ready(val app: FlashcardApp, val candidates: List<FlashcardApp>) : FlashcardAppState
}

/**
 * Decides which flashcard app to talk to.
 *
 *   stored pin valid? ──yes──> Ready(pinned)
 *        │ no (uninstalled → None, cert/authority changed → Revoked)
 *        v
 *   Revoked? ──yes──> NeedsChoice
 *        │ no
 *        v
 *   AnkiDroid usable? ──yes──> pin it (first use), Ready(AnkiDroid)
 *        │ no
 *        v
 *   NeedsChoice, or NoneInstalled
 */
class FlashcardAppResolver(
    private val installed: InstalledApps,
    private val pins: PinStore,
) {

    fun resolve(): FlashcardAppState {
        val candidates = candidates()
        val choice = checkPin(pins.load(), candidates)

        // A valid pin wins.
        if (choice is StoredChoice.Pinned) {
            return FlashcardAppState.Ready(choice.app, candidates)
        }

        if (candidates.isEmpty()) {
            return FlashcardAppState.NoneInstalled
        }

        // A broken pin asks again instead of switching silently.
        if (choice == StoredChoice.Revoked) {
            return FlashcardAppState.NeedsChoice(candidates)
        }

        // No choice yet: only the default is used without asking.
        val default = FlashcardApp.DEFAULT
        if (default !in candidates) {
            return FlashcardAppState.NeedsChoice(candidates)
        }
        if (!pin(default)) {
            return FlashcardAppState.NeedsChoice(candidates)
        }
        return FlashcardAppState.Ready(default, candidates)
    }

    /** User picked [app] from the list; pins its current certificate. */
    fun choose(app: FlashcardApp): Boolean {
        if (app !in candidates()) {
            return false
        }
        return pin(app)
    }

    /** Installed apps whose authority is held by the expected package. */
    private fun candidates(): List<FlashcardApp> = FlashcardApp.entries.filter { app ->
        installed.signerDigests(app.packageName).isNotEmpty() &&
            installed.authorityHolder(app.authority) == app.packageName
    }

    /** Validates the stored choice; persists the downgrade when it no longer holds. */
    private fun checkPin(choice: StoredChoice, candidates: List<FlashcardApp>): StoredChoice {
        if (choice !is StoredChoice.Pinned) {
            return choice
        }

        val digests = installed.signerDigests(choice.app.packageName)

        // Uninstalled: forget it, the default takes over.
        if (digests.isEmpty()) {
            pins.save(StoredChoice.None)
            return StoredChoice.None
        }

        // Different certificate or package, or authority squatted: ask again.
        val valid = choice.app in candidates &&
            choice.packageName == choice.app.packageName &&
            choice.digest in digests
        if (!valid) {
            pins.save(StoredChoice.Revoked)
            return StoredChoice.Revoked
        }
        return choice
    }

    private fun pin(app: FlashcardApp): Boolean {
        val digest = installed.signerDigests(app.packageName).minOrNull() ?: return false
        pins.save(StoredChoice.Pinned(app, app.packageName, digest))
        return true
    }
}
