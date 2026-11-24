// SPDX-License-Identifier: AGPL-3.0-or-later

package org.ankiquest.garmanki.anki

import android.content.Context

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
