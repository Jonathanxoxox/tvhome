package io.github.jonathanxoxox.mayohome.system

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Merkt sich den letzten Absturz in einer Datei, damit man ihn ohne ADB sieht: beim nächsten Start
 * als kurzer Hinweis und unter Einstellungen → Über. Als Startbildschirm startet Mayo Home nach einem
 * Absturz sofort neu; ohne diese Notiz wäre der Fehler unsichtbar.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"
    private const val SEEN = "last_crash_seen"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val time = SimpleDateFormat("dd.MM. HH:mm:ss", Locale.GERMAN).format(Date())
                File(app.filesDir, FILE).writeText("$time · Version ${io.github.jonathanxoxox.mayohome.BuildConfig.VERSION_NAME}\n" + error.stackTraceToString().take(6000))
                File(app.filesDir, SEEN).delete()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun last(context: Context): String? = File(context.filesDir, FILE).takeIf { it.isFile }?.readText()

    /** Der Absturz, falls er noch nicht gemeldet wurde; danach gilt er als gesehen. */
    fun takeUnseen(context: Context): String? {
        val seen = File(context.filesDir, SEEN)
        if (seen.exists()) return null
        val text = last(context) ?: return null
        seen.writeText("1")
        return text
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
        File(context.filesDir, SEEN).delete()
    }
}
