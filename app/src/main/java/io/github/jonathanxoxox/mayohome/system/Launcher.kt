package io.github.jonathanxoxox.mayohome.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import io.github.jonathanxoxox.mayohome.MainActivity

/** Hilfen rund um "Mayo Home ist der Startbildschirm". */
object Launcher {

    /** Vorinstallierte Startbildschirme, die der Home-Wächter durch Mayo Home ersetzt. */
    val STOCK_LAUNCHERS = setOf(
        "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx",
        "com.google.android.leanbacklauncher",
        "com.android.tv.launcher",
        "com.amazon.tv.launcher",
        "com.mitv.tvhome",
        "com.tcl.home",
    )

    const val EXTRA_FROM_HOME = "mayo_from_home"

    fun homeIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME)
        .putExtra(EXTRA_FROM_HOME, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun openHome(context: Context) {
        runCatching { context.startActivity(homeIntent(context)) }
    }

    /** Ist Mayo Home die Standard-App für die Home-Taste? */
    fun isDefaultHome(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = context.packageManager.resolveActivity(intent, 0) ?: return false
        return info.activityInfo?.packageName == context.packageName
    }

    /** Ist der Home-Wächter in den Bedienungshilfen eingeschaltet? */
    fun isGuardEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val me = ComponentName(context, HomeGuardService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Darf Mayo Home die Bedienungshilfen selbst einschalten? (einmal per ADB erlaubt) */
    fun canWriteSecure(context: Context): Boolean =
        context.checkSelfPermission(WRITE_SECURE) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Trägt den Home-Wächter in die Liste der Bedienungshilfen ein und lässt alle anderen (Projectivy,
     * Button Mapper …) stehen. Geht nur mit der per ADB erteilten Berechtigung; sonst false.
     */
    fun enableGuard(context: Context): Boolean {
        if (!canWriteSecure(context)) return false
        return try {
            val resolver = context.contentResolver
            val me = ComponentName(context, HomeGuardService::class.java)
            val parts = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                .split(':').filter { it.isNotBlank() }
            if (parts.none { ComponentName.unflattenFromString(it) == me }) {
                Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (parts + me.flattenToString()).joinToString(":"))
            }
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Einmal per ADB: danach schaltet Mayo Home den Home-Wächter selbst ein, ohne andere Dienste anzufassen. */
    fun guardAdbCommand(context: Context): String =
        "adb shell pm grant ${context.packageName} $WRITE_SECURE"

    private const val WRITE_SECURE = "android.permission.WRITE_SECURE_SETTINGS"

    fun homeAdbCommand(context: Context): String =
        "adb shell cmd package set-home-activity ${context.packageName}/.MainActivity"

    fun overlayAdbCommand(context: Context): String =
        "adb shell appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow"

    fun epgAdbCommand(context: Context): String =
        "adb shell pm grant ${context.packageName} com.android.providers.tv.permission.READ_EPG_DATA\n" +
            "adb shell pm grant ${context.packageName} android.permission.READ_TV_LISTINGS"

    /** Öffnet eine Systemseite; manche Boxen haben sie nicht, dann gibt es einen Hinweis. */
    fun openSystemPage(context: Context, action: String, data: Uri? = null) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (data != null) intent.data = data
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Diese Einstellungsseite gibt es auf dieser Box nicht. Nutze den ADB-Befehl.", Toast.LENGTH_LONG).show()
        }
    }

    fun start(context: Context, intent: Intent?): Boolean {
        if (intent == null) return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            Toast.makeText(context, "Konnte nicht geöffnet werden.", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
