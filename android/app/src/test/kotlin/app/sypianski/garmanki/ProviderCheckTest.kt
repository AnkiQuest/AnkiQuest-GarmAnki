// SPDX-License-Identifier: AGPL-3.0-or-later

package app.sypianski.garmanki

import app.sypianski.garmanki.anki.ANKIDROID_FDROID_KEY
import app.sypianski.garmanki.anki.ANKIDROID_KEYS
import app.sypianski.garmanki.anki.ANKIDROID_RELEASE_KEY
import app.sypianski.garmanki.anki.CompanionBuild
import app.sypianski.garmanki.anki.InstalledApps
import app.sypianski.garmanki.anki.ProviderCheck
import app.sypianski.garmanki.anki.ProviderTrust
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderCheckTest {

    /** package → signer digests; authority → holding package. */
    private class FakeInstalled : InstalledApps {
        val signers = mutableMapOf<String, Set<String>>()
        val authorities = mutableMapOf<String, String>()

        override fun signerDigests(packageName: String) = signers[packageName].orEmpty()
        override fun authorityHolder(authority: String) = authorities[authority]
    }

    private val installed = FakeInstalled()
    private val keys = ANKIDROID_KEYS

    private fun installAnkiDroid(vararg digests: String) {
        installed.signers[PACKAGE] = digests.toSet()
        installed.authorities[AUTHORITY] = PACKAGE
    }

    private fun check(build: CompanionBuild = CompanionBuild.RELEASE) =
        ProviderCheck(installed, build).check(PACKAGE, AUTHORITY, keys)

    @Test
    fun `genuine AnkiDroid is trusted`() {
        // PackageManager digests are lower-case hex.
        installAnkiDroid(ANKIDROID_RELEASE_KEY.lowercase())

        assertEquals(ProviderTrust.TRUSTED, check())
    }

    @Test
    fun `AnkiDroid from F-Droid is trusted`() {
        installAnkiDroid(ANKIDROID_FDROID_KEY.lowercase())

        assertEquals(ProviderTrust.TRUSTED, check())
    }

    @Test
    fun `another package holding the authority is rejected`() {
        installed.signers[SQUATTER] = setOf(ANKIDROID_RELEASE_KEY)
        installed.authorities[AUTHORITY] = SQUATTER

        assertEquals(ProviderTrust.WRONG_HOLDER, check())
        assertEquals(ProviderTrust.WRONG_HOLDER, check(CompanionBuild.DEBUGGABLE))
    }

    @Test
    fun `AnkiDroid package with an unknown key is rejected`() {
        installAnkiDroid(OTHER_KEY)

        assertEquals(ProviderTrust.UNKNOWN_KEY, check())
    }

    @Test
    fun `unknown key is accepted by a debuggable companion`() {
        installAnkiDroid(OTHER_KEY)

        assertEquals(ProviderTrust.TRUSTED, check(CompanionBuild.DEBUGGABLE))
    }

    @Test
    fun `rotated key matches through the certificate history`() {
        installAnkiDroid(ANKIDROID_RELEASE_KEY, OTHER_KEY)

        assertEquals(ProviderTrust.TRUSTED, check())
    }

    @Test
    fun `not installed`() {
        assertEquals(ProviderTrust.NOT_INSTALLED, check())
        assertEquals(ProviderTrust.NOT_INSTALLED, check(CompanionBuild.DEBUGGABLE))
    }

    private companion object {
        const val PACKAGE = "com.ichi2.anki"
        const val AUTHORITY = "com.ichi2.anki.flashcards"
        const val SQUATTER = "com.example.squatter"
        const val OTHER_KEY = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
    }
}
