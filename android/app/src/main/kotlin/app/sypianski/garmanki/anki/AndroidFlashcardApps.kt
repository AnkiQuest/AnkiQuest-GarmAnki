package app.sypianski.garmanki.anki

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.graphics.drawable.Drawable
import android.os.Build
import java.security.MessageDigest

/** [InstalledApps] backed by PackageManager. */
class AndroidInstalledApps(private val context: Context) : InstalledApps {

    private val pm get() = context.packageManager

    override fun signerDigests(packageName: String): Set<String> = try {
        signatures(packageName).map { sha256(it.toByteArray()) }.toSet()
    } catch (e: PackageManager.NameNotFoundException) {
        emptySet()
    }

    override fun authorityHolder(authority: String): String? =
        pm.resolveContentProvider(authority, 0)?.packageName

    fun label(app: FlashcardApp): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(app.packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        app.packageName
    }

    fun icon(app: FlashcardApp): Drawable? = try {
        pm.getApplicationIcon(app.packageName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** Current signers; with key rotation, the whole lineage. */
    private fun signatures(packageName: String): List<Signature> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            return info.signatures?.toList().orEmpty()
        }

        val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signing = info.signingInfo ?: return emptyList()
        if (signing.hasMultipleSigners()) {
            return signing.apkContentsSigners?.toList().orEmpty()
        }
        return signing.signingCertificateHistory?.toList().orEmpty()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

/** [PinStore] in SharedPreferences: synchronous, read before every provider call. */
class PrefsPinStore(context: Context) : PinStore {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): StoredChoice {
        if (prefs.getBoolean(KEY_REVOKED, false)) {
            return StoredChoice.Revoked
        }

        val name = prefs.getString(KEY_APP, null) ?: return StoredChoice.None
        val app = FlashcardApp.entries.firstOrNull { it.name == name } ?: return StoredChoice.Revoked
        val pkg = prefs.getString(KEY_PACKAGE, null) ?: return StoredChoice.Revoked
        val digest = prefs.getString(KEY_DIGEST, null) ?: return StoredChoice.Revoked
        return StoredChoice.Pinned(app, pkg, digest)
    }

    override fun save(choice: StoredChoice) {
        val edit = prefs.edit().clear()
        when (choice) {
            StoredChoice.None -> {}
            StoredChoice.Revoked -> edit.putBoolean(KEY_REVOKED, true)
            is StoredChoice.Pinned -> edit
                .putString(KEY_APP, choice.app.name)
                .putString(KEY_PACKAGE, choice.packageName)
                .putString(KEY_DIGEST, choice.digest)
        }
        edit.apply()
    }

    private companion object {
        const val PREFS = "flashcard_app"
        const val KEY_APP = "app"
        const val KEY_PACKAGE = "package"
        const val KEY_DIGEST = "sha256"
        const val KEY_REVOKED = "revoked"
    }
}
