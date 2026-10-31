package io.github.jonathanxoxox.mayohome.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Sichert die Einstellungen als JSON-Datei im Download-Ordner der Box, so dass man sie mit einem
 * Dateimanager kopieren oder nach einem Neuaufsetzen wieder einlesen kann.
 */
object Backup {
    const val FILE_NAME = "mayo-home-einstellungen.json"

    suspend fun export(context: Context, settings: Settings): String = withContext(Dispatchers.IO) {
        val text = settings.toJson().toString(2)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val uri = find(context) ?: resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                },
            ) ?: error("Download-Ordner nicht beschreibbar")
            resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: error("Datei nicht beschreibbar")
            "Download/$FILE_NAME"
        } else {
            val file = File(context.getExternalFilesDir(null), FILE_NAME)
            file.writeText(text)
            file.absolutePath
        }
    }

    suspend fun import(context: Context): Settings? = withContext(Dispatchers.IO) {
        val text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = find(context) ?: return@withContext null
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        } else {
            File(context.getExternalFilesDir(null), FILE_NAME).takeIf { it.isFile }?.readText()
        }
        text?.let { Settings.parse(it) }
    }

    private fun find(context: Context): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            collection, arrayOf(MediaStore.Downloads._ID), "${MediaStore.Downloads.DISPLAY_NAME} = ?", arrayOf(FILE_NAME), null,
        )?.use { c ->
            if (c.moveToFirst()) return Uri.withAppendedPath(collection, c.getLong(0).toString())
        }
        return null
    }
}
