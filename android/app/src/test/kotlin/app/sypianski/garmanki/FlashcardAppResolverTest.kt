// SPDX-License-Identifier: AGPL-3.0-or-later

package app.sypianski.garmanki

import app.sypianski.garmanki.anki.ANKIDROID_RELEASE_KEY
import app.sypianski.garmanki.anki.CompanionBuild
import app.sypianski.garmanki.anki.FlashcardApp
import app.sypianski.garmanki.anki.FlashcardAppResolver
import app.sypianski.garmanki.anki.FlashcardAppState
import app.sypianski.garmanki.anki.InstalledApps
import app.sypianski.garmanki.anki.PinStore
import app.sypianski.garmanki.anki.StoredChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashcardAppResolverTest {

    private val ankiDroid = FlashcardApp.ANKIDROID
    private val ankiQuest = FlashcardApp.ANKIQUEST
    private val ankiQuestReleased = FlashcardApp.ANKIQUEST_RELEASED

    /** package → signer digests; authority → holding package. */
    private class FakeInstalled : InstalledApps {
        val signers = mutableMapOf<String, Set<String>>()
        val authorities = mutableMapOf<String, String>()

        fun install(app: FlashcardApp, digest: String) {
            signers[app.packageName] = setOf(digest)
            authorities[app.authority] = app.packageName
        }

        fun uninstall(app: FlashcardApp) {
            signers.remove(app.packageName)
            authorities.remove(app.authority)
        }

        override fun signerDigests(packageName: String) = signers[packageName].orEmpty()
        override fun authorityHolder(authority: String) = authorities[authority]
    }

    private class FakePins : PinStore {
        var choice: StoredChoice = StoredChoice.None
        override fun load() = choice
        override fun save(choice: StoredChoice) {
            this.choice = choice
        }
    }

    private val installed = FakeInstalled()
    private val pins = FakePins()
    private val resolver = FlashcardAppResolver(installed, pins, CompanionBuild.RELEASE)

    @Test
    fun `none installed`() {
        assertEquals(FlashcardAppState.NoneInstalled, resolver.resolve())
        assertEquals(StoredChoice.None, pins.choice)
    }

    @Test
    fun `AnkiDroid with its release key is used without asking or pinning`() {
        // PackageManager digests are lower-case hex.
        installed.install(ankiDroid, ANKIDROID_RELEASE_KEY.lowercase())

        assertEquals(FlashcardAppState.Ready(ankiDroid, listOf(ankiDroid)), resolver.resolve())
        assertEquals(StoredChoice.None, pins.choice)
    }

    @Test
    fun `AnkiQuest only needs an explicit choice`() {
        installed.install(ankiQuest, "aq")

        assertEquals(FlashcardAppState.NeedsChoice(listOf(ankiQuest)), resolver.resolve())
        assertEquals(StoredChoice.None, pins.choice)

        assertTrue(resolver.choose(ankiQuest))
        assertEquals(FlashcardAppState.Ready(ankiQuest, listOf(ankiQuest)), resolver.resolve())
    }

    @Test
    fun `released AnkiQuest only needs an explicit choice`() {
        installed.install(ankiQuestReleased, "aq-released")

        assertEquals(FlashcardAppState.NeedsChoice(listOf(ankiQuestReleased)), resolver.resolve())

        assertTrue(resolver.choose(ankiQuestReleased))
        assertEquals(FlashcardAppState.Ready(ankiQuestReleased, listOf(ankiQuestReleased)), resolver.resolve())
    }

    @Test
    fun `both AnkiQuest packages installed are both offered`() {
        installed.install(ankiQuest, "aq")
        installed.install(ankiQuestReleased, "aq-released")
        val both = listOf(ankiQuest, ankiQuestReleased)

        assertEquals(FlashcardAppState.NeedsChoice(both), resolver.resolve())

        assertTrue(resolver.choose(ankiQuestReleased))
        assertEquals(FlashcardAppState.Ready(ankiQuestReleased, both), resolver.resolve())
    }

    @Test
    fun `both installed defaults to AnkiDroid until AnkiQuest is chosen`() {
        installed.install(ankiDroid, ANKIDROID_RELEASE_KEY)
        installed.install(ankiQuest, "aq")
        val both = listOf(ankiDroid, ankiQuest)

        assertEquals(FlashcardAppState.Ready(ankiDroid, both), resolver.resolve())

        assertTrue(resolver.choose(ankiQuest))
        assertEquals(FlashcardAppState.Ready(ankiQuest, both), resolver.resolve())
    }

    @Test
    fun `chosen app uninstalled falls back to the default`() {
        installed.install(ankiDroid, ANKIDROID_RELEASE_KEY)
        installed.install(ankiQuest, "aq")
        resolver.choose(ankiQuest)

        installed.uninstall(ankiQuest)

        assertEquals(FlashcardAppState.Ready(ankiDroid, listOf(ankiDroid)), resolver.resolve())
    }

    @Test
    fun `changed certificate resets the choice and asks again`() {
        installed.install(ankiDroid, ANKIDROID_RELEASE_KEY)
        installed.install(ankiQuest, "aq")
        resolver.choose(ankiQuest)

        installed.install(ankiQuest, "evil")

        assertEquals(FlashcardAppState.NeedsChoice(listOf(ankiDroid, ankiQuest)), resolver.resolve())
        assertEquals(StoredChoice.Revoked, pins.choice)

        // Asking again is sticky until the user picks.
        assertEquals(FlashcardAppState.NeedsChoice(listOf(ankiDroid, ankiQuest)), resolver.resolve())
    }

    @Test
    fun `AnkiDroid with an unknown key is not used`() {
        installed.install(ankiDroid, ANKIDROID_RELEASE_KEY)
        resolver.resolve()

        installed.install(ankiDroid, "evil")

        assertEquals(FlashcardAppState.Unverified, resolver.resolve())
        assertFalse(resolver.choose(ankiDroid))
    }

    @Test
    fun `debuggable companion uses a self-built AnkiDroid`() {
        installed.install(ankiDroid, "self-built")
        val debug = FlashcardAppResolver(installed, pins, CompanionBuild.DEBUGGABLE)

        assertEquals(FlashcardAppState.Ready(ankiDroid, listOf(ankiDroid)), debug.resolve())
    }

    @Test
    fun `choosing AnkiDroid again drops the AnkiQuest pin`() {
        installed.install(ankiDroid, ANKIDROID_RELEASE_KEY)
        installed.install(ankiQuest, "aq")
        resolver.choose(ankiQuest)

        assertTrue(resolver.choose(ankiDroid))

        assertEquals(StoredChoice.None, pins.choice)
        assertEquals(FlashcardAppState.Ready(ankiDroid, listOf(ankiDroid, ankiQuest)), resolver.resolve())
    }

    @Test
    fun `authority held by an unexpected package is rejected`() {
        installed.signers[ankiDroid.packageName] = setOf(ANKIDROID_RELEASE_KEY)
        installed.authorities[ankiDroid.authority] = "com.example.squatter"

        assertEquals(FlashcardAppState.Unverified, resolver.resolve())
        assertFalse(resolver.choose(ankiDroid))
    }

    @Test
    fun `squatted authority revokes an existing pin`() {
        installed.install(ankiDroid, ANKIDROID_RELEASE_KEY)
        installed.install(ankiQuest, "aq")
        resolver.choose(ankiQuest)

        installed.authorities[ankiQuest.authority] = "com.example.squatter"

        assertEquals(FlashcardAppState.NeedsChoice(listOf(ankiDroid)), resolver.resolve())
    }
}
