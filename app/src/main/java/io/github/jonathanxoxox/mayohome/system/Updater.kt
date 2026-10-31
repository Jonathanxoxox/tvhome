package io.github.jonathanxoxox.mayohome.system

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import io.github.jonathanxoxox.mayohome.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Eine neuere Version aus dem Release "latest" auf GitHub. */
data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String)

/**
 * Sucht im Release "latest" von GitHub nach einer neueren APK, lädt sie und übergibt sie dem
 * Paket-Installer von Android. Die Versionsnummer steht in version.json neben der APK.
 */
class Updater(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(VERSION_URL).header("Cache-Control", "no-cache").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("GitHub antwortet mit ${response.code}")
            val json = JSONObject(response.body.string())
            val info = UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.optString("versionName"),
                apkUrl = json.optString("apkUrl").ifBlank { APK_URL },
            )
            info.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
        }
    }

    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "MayoHome-${info.versionCode}.apk")
        client.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) error("Download fehlgeschlagen (${response.code})")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: -1L
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        }
        file
    }

    /** Darf Mayo Home Apps installieren? Sonst muss der Nutzer das einmal in den Einstellungen erlauben. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        Launcher.openSystemPage(context, Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
    }

    suspend fun install(file: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("base.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(
                context, sessionId, Intent(context, InstallResultReceiver::class.java), flags,
            )
            session.commit(callback.intentSender)
        }
    }

    companion object {
        const val BASE = "https://github.com/Jonathanxoxox/tvhome/releases/download/latest"
        const val VERSION_URL = "$BASE/version.json"
        const val APK_URL = "$BASE/MayoHome.apk"
    }
}

/** Ergebnis des Paket-Installers: Bestätigung anzeigen oder Fehler melden. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> Toast.makeText(
                context,
                "Update passt nicht zur installierten Version (anderer Schlüssel). Mayo Home einmal deinstallieren und die neue APK installieren.",
                Toast.LENGTH_LONG,
            ).show()
            else -> Toast.makeText(
                context,
                "Update fehlgeschlagen: " + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unbekannter Fehler"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
