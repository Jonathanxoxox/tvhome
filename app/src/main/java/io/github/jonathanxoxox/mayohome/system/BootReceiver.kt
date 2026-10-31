package io.github.jonathanxoxox.mayohome.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.github.jonathanxoxox.mayohome.MayoHomeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Nach dem Einschalten: Mayo Home öffnen, oder die App, die in den Einstellungen gewählt ist. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                BootStarter.run(context.applicationContext, source = "boot")
            } finally {
                pending.finish()
            }
        }
    }
}

object BootStarter {
    /** Nur einmal pro Einschalten, egal ob Empfänger oder Home-Wächter zuerst kommt. */
    private var doneAtBoot = -1L

    suspend fun run(context: Context, @Suppress("UNUSED_PARAMETER") source: String) {
        val bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        if (kotlin.math.abs(bootTime - doneAtBoot) < 60_000L) return
        val app = context.applicationContext as MayoHomeApp
        // Gespeicherte Einstellungen lesen, statt mit den Standardwerten zu starten.
        val settings = withTimeoutOrNull(3_000) { app.settings.current() } ?: app.settings.state.value
        if (!settings.openOnBoot) return
        doneAtBoot = bootTime
        val target = settings.bootApp.takeIf { it.isNotBlank() }?.let { pkg ->
            context.packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: context.packageManager.getLaunchIntentForPackage(pkg)
        }
        if (target != null) Launcher.start(context, target) else Launcher.openHome(context)
    }
}

/** Startet [block] im Hintergrund, ohne auf das Ergebnis zu warten. */
fun BootStarter.runAsync(context: Context, source: String) {
    CoroutineScope(Dispatchers.Main).launch { run(context, source) }
}
