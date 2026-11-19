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
    val key: SigningKey,
) {
    ANKIDROID(
        "com.ichi2.anki",
        "com.ichi2.anki.flashcards",
        "com.ichi2.anki.permission.READ_WRITE_DATABASE",
        SigningKey.BuiltIn(ANKIDROID_RELEASE_KEY),
    ),
    ANKIQUEST(
        "org.ankiquest.anki",
        "org.ankiquest.anki.flashcards",
        "org.ankiquest.anki.permission.READ_WRITE_DATABASE",
        SigningKey.PinnedOnChoice,
    ),
    ;

    companion object {
        /** Used without asking when it passes the release-key check; every other app is opt-in. */
        val DEFAULT = ANKIDROID
    }
}

/** How the companion knows an app's signing key. */
sealed interface SigningKey {
    /** Release key built into the companion. */
    data class BuiltIn(val sha256: String) : SigningKey

    /** Not known here: pinned when the user chooses the app. */
    data object PinnedOnChoice : SigningKey
}

/** What the user last chose. */
sealed interface StoredChoice {
    /** Nothing chosen yet: the default is used if installed. */
    data object None : StoredChoice

    /** [SigningKey.PinnedOnChoice] app chosen, bound to its package and signing certificate. */
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

    /** Nothing usable, and AnkiDroid's authority answered but failed [ProviderCheck]. */
    data object Unverified : FlashcardAppState

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
 *   AnkiDroid with its release key? ──yes──> Ready(AnkiDroid)
 *        │ no
 *        v
 *   NeedsChoice, Unverified or NoneInstalled
 */
class FlashcardAppResolver(
    private val installed: InstalledApps,
    private val pins: PinStore,
    build: CompanionBuild,
) {

    private val check = ProviderCheck(installed, build)

    fun resolve(): FlashcardAppState {
        val candidates = candidates()
        val choice = checkPin(pins.load(), candidates)

        // A valid pin wins.
        if (choice is StoredChoice.Pinned) {
            return FlashcardAppState.Ready(choice.app, candidates)
        }

        if (candidates.isEmpty()) {
            return noneUsable()
        }

        // A broken pin asks again instead of switching silently.
        if (choice == StoredChoice.Revoked) {
            return FlashcardAppState.NeedsChoice(candidates)
        }

        // No choice yet: only the default is used without asking; its key is built in.
        val default = FlashcardApp.DEFAULT
        if (default !in candidates) {
            return FlashcardAppState.NeedsChoice(candidates)
        }
        return FlashcardAppState.Ready(default, candidates)
    }

    /** User picked [app] from the list; pins its current certificate unless the key is built in. */
    fun choose(app: FlashcardApp): Boolean {
        if (app !in candidates()) {
            return false
        }

        // Back to the default: no pin, the release-key check runs on every call.
        if (app == FlashcardApp.DEFAULT) {
            pins.save(StoredChoice.None)
            return true
        }
        return pin(app)
    }

    /** Installed apps that pass [ProviderCheck]. */
    private fun candidates(): List<FlashcardApp> =
        FlashcardApp.entries.filter { trust(it) == ProviderTrust.TRUSTED }

    /** A built-in key is checked here; a pinned key only once chosen, in [checkPin]. */
    private fun trust(app: FlashcardApp): ProviderTrust = when (val key = app.key) {
        is SigningKey.BuiltIn -> check.check(app.packageName, app.authority, setOf(key.sha256))
        SigningKey.PinnedOnChoice -> check.holder(app.packageName, app.authority)
    }

    /** Says "could not be verified" rather than "not installed" when AnkiDroid's authority answered. */
    private fun noneUsable(): FlashcardAppState {
        if (trust(FlashcardApp.DEFAULT) == ProviderTrust.NOT_INSTALLED) {
            return FlashcardAppState.NoneInstalled
        }
        return FlashcardAppState.Unverified
    }

    /** Validates the stored choice; persists the downgrade when it no longer holds. */
    private fun checkPin(choice: StoredChoice, candidates: List<FlashcardApp>): StoredChoice {
        if (choice !is StoredChoice.Pinned) {
            return choice
        }

        // Uninstalled: forget it, the default takes over.
        if (installed.signerDigests(choice.app.packageName).isEmpty()) {
            pins.save(StoredChoice.None)
            return StoredChoice.None
        }

        // Different certificate or package, or authority squatted: ask again.
        val valid = choice.app in candidates &&
            choice.packageName == choice.app.packageName &&
            check.pinned(choice.packageName, choice.app.authority, choice.digest) == ProviderTrust.TRUSTED
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
