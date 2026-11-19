package app.sypianski.garmanki.anki

/**
 * SHA-256 of AnkiDroid's release signing certificate (Google Play and
 * GitHub releases, CN=Nicolas Raoul, O=http://nrw.free.fr), as
 * `apksigner verify --print-certs` prints it.
 */
const val ANKIDROID_RELEASE_KEY = "2071534F0F4B5E54AE952DD275D70DA6E3459EE69909D2AB1B4843C4C5B21A45"

/** Platform seam: what PackageManager says about installed apps. */
interface InstalledApps {
    /** SHA-256 hex digests of the package's signing certificates; empty if not installed. */
    fun signerDigests(packageName: String): Set<String>

    /** Package that currently holds the provider authority, or null. */
    fun authorityHolder(authority: String): String?
}

/** How the companion itself was built. */
enum class CompanionBuild {
    RELEASE,

    /** Developer build: also trusts a self-built provider app signed with another key. */
    DEBUGGABLE,
}

/** Whether a provider app may be called, and if not, why. */
enum class ProviderTrust {
    TRUSTED,
    NOT_INSTALLED,

    /** Another package holds the authority. */
    WRONG_HOLDER,

    /** The expected package, but not signed with a known key. */
    UNKNOWN_KEY,
}

/**
 * Checks, before every provider call, that the authority belongs to the
 * expected package and that the package is signed with a known key. An
 * app squatting AnkiDroid's authority while AnkiDroid is missing never
 * receives the companion's calls.
 */
class ProviderCheck(
    private val installed: InstalledApps,
    private val build: CompanionBuild,
) {

    /** Checks [packageName] against release [keys] built into the companion. */
    fun check(packageName: String, authority: String, keys: Set<String>): ProviderTrust {
        val trust = verify(packageName, authority, keys)

        // A self-built provider app is fine in a developer build of the companion.
        if (trust == ProviderTrust.UNKNOWN_KEY && build == CompanionBuild.DEBUGGABLE) {
            return ProviderTrust.TRUSTED
        }
        return trust
    }

    private fun verify(packageName: String, authority: String, keys: Set<String>): ProviderTrust {
        val holder = installed.authorityHolder(authority) ?: return ProviderTrust.NOT_INSTALLED
        if (holder != packageName) {
            return ProviderTrust.WRONG_HOLDER
        }

        val digests = installed.signerDigests(packageName)
        if (digests.isEmpty()) {
            return ProviderTrust.NOT_INSTALLED
        }

        // Any signer in the lineage matches, so a rotated key keeps working.
        val known = digests.any { digest -> keys.any { it.equals(digest, ignoreCase = true) } }
        if (!known) {
            return ProviderTrust.UNKNOWN_KEY
        }
        return ProviderTrust.TRUSTED
    }
}
