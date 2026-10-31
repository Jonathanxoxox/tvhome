package io.github.jonathanxoxox.mayohome.system

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import io.github.jonathanxoxox.mayohome.MayoHomeApp

/**
 * Der Home-Wächter: Sobald der vorinstallierte Startbildschirm im Vordergrund erscheint (Home-Taste,
 * Zurück aus einer App, nach dem Einschalten), öffnet er Mayo Home. So arbeitet auch Projectivy auf
 * Boxen, die keinen anderen Standard-Launcher zulassen. Inhalte von Fenstern liest er nicht.
 */
class HomeGuardService : AccessibilityService() {

    private var lastJump = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Der Dienst startet beim Hochfahren mit; das ist der verlässlichste Moment für "beim Einschalten öffnen".
        // Ein Fehler hier darf den Dienst nicht abstürzen lassen, sonst schaltet Android ihn ab.
        runCatching { if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) BootStarter.runAsync(this, source = "guard") }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        runCatching { handle(event) }
    }

    private fun handle(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in Launcher.STOCK_LAUNCHERS) return
        val settings = (application as MayoHomeApp).settings.state.value
        if (!settings.homeOverride) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastJump < 800) return
        lastJump = now
        Launcher.openHome(this)
    }

    override fun onInterrupt() = Unit

    companion object {
        const val BOOT_WINDOW_MS = 3 * 60_000L
    }
}
