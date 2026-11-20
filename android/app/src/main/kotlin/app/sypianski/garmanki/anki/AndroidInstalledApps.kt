// SPDX-License-Identifier: AGPL-3.0-or-later

package app.sypianski.garmanki.anki

import android.content.Context
import android.content.pm.ApplicationInfo
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

/** Whether this companion is a developer build (FLAG_DEBUGGABLE). */
fun companionBuild(context: Context): CompanionBuild {
    val flags = context.applicationInfo.flags
    if (flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
        return CompanionBuild.DEBUGGABLE
    }
    return CompanionBuild.RELEASE
}
