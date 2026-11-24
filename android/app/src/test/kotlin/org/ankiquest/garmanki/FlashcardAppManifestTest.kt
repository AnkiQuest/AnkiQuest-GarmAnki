// SPDX-License-Identifier: AGPL-3.0-or-later

package org.ankiquest.garmanki

import org.ankiquest.garmanki.anki.FlashcardApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FlashcardAppManifestTest {

    /** Without these, Android hides the app (package visibility) or never shows its permission dialog. */
    @Test
    fun `manifest declares every flashcard app`() {
        // Gradle runs unit tests from the module directory.
        val manifest = File("src/main/AndroidManifest.xml").readText()

        for (app in FlashcardApp.entries) {
            assertTrue(app.permission, manifest.contains("<uses-permission android:name=\"${app.permission}\""))
            assertTrue(app.packageName, manifest.contains("<package android:name=\"${app.packageName}\""))
            assertTrue(app.authority, manifest.contains("<provider android:authorities=\"${app.authority}\""))
        }
    }

    /** Every AnkiQuest build installs as org.ankiquest.ankidroid (AnkiQuest-Android#461). */
    @Test
    fun `AnkiQuest is org dot ankiquest`() {
        val app = FlashcardApp.ANKIQUEST

        assertEquals("org.ankiquest.ankidroid", app.packageName)
        assertEquals("org.ankiquest.ankidroid.flashcards", app.authority)
        assertEquals("org.ankiquest.ankidroid.permission.READ_WRITE_DATABASE", app.permission)
    }
}
