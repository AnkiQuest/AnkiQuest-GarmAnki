package org.ankiquest.garmanki

import android.app.Application
import org.ankiquest.garmanki.anki.AnkiDroidClient
import org.ankiquest.garmanki.ciq.CiqManager
import org.ankiquest.garmanki.data.SettingsStore
import org.ankiquest.garmanki.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual singletons — no DI framework (house pattern from notes_and_codes). */
class App : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var anki: AnkiDroidClient
        private set
    lateinit var ciq: CiqManager
        private set
    lateinit var engine: SyncEngine
        private set

    /**
     * Process-lifetime scope: pushes and answer replays must survive the
     * user navigating away mid-operation (watch ACK timeout is 30 s).
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        anki = AnkiDroidClient(this)
        ciq = CiqManager(this)
        engine = SyncEngine(anki, ciq, settings, appScope)
        engine.start()
    }
}
