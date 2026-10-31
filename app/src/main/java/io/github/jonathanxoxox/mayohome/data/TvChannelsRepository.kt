package io.github.jonathanxoxox.mayohome.data

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ein Eintrag aus einem App-Kanal oder aus "Weiter schauen" des Systems. */
data class ChannelProgram(
    val id: Long,
    val packageName: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val posterUri: String?,
    /** Breite durch Höhe des Posters, z. B. 16/9 oder 2/3. */
    val aspect: Float,
    val intentUri: String?,
    val live: Boolean,
    val progress: Float?,
    val lastEngagement: Long,
)

/** Ein Kanal, den eine App auf dem Android-TV-Startbildschirm anlegt (z. B. "Weiter schauen" in Nuvio). */
data class AppChannel(
    val id: Long,
    val packageName: String,
    val name: String,
    val internalId: String,
    val programs: List<ChannelProgram>,
) {
    /** Schlüssel zum Ausblenden in den Einstellungen; bleibt gleich, auch wenn die App den Kanal neu anlegt. */
    val key: String get() = "$packageName/${internalId.ifBlank { name }}"
}

data class TvSnapshot(
    val channels: List<AppChannel> = emptyList(),
    val watchNext: List<ChannelProgram> = emptyList(),
    /** True, wenn Android uns die Kanäle anderer Apps nicht lesen lässt. */
    val permissionMissing: Boolean = false,
) {
    fun mayo(key: String): List<ChannelProgram> =
        channels.firstOrNull { it.packageName == MAYO_TV && it.internalId == key }?.programs.orEmpty()

    /** Mayo TVs "Zuletzt gesehen"-Kanal hat eine Kennung, die mit diesem Text beginnt. */
    fun mayoRecent(): List<ChannelProgram> =
        channels.firstOrNull { it.packageName == MAYO_TV && it.internalId.startsWith("owntv:recent_live") }?.programs.orEmpty()

    companion object {
        const val MAYO_TV = "io.github.jonathanxoxox.tvplayer"
        const val MAYO_FOR_YOU = "tvplayer_for_you"
        const val MAYO_FAVORITES = "tvplayer_favorites"
    }
}

/**
 * Liest die Kanäle der Apps und "Weiter schauen" aus dem TV-Provider von Android. Das geht nur, wenn
 * Android uns die Berechtigung für Programmdaten gibt; sonst kommt ein leerer Stand mit [TvSnapshot.permissionMissing].
 */
class TvChannelsRepository(private val context: Context) {

    suspend fun load(): TvSnapshot = withContext(Dispatchers.IO) {
        try {
            val channels = loadChannels()
            val watchNext = loadPrograms(TvContractCompat.WatchNextPrograms.CONTENT_URI, watchNext = true)
                .sortedByDescending { it.lastEngagement }
            // Liefert der Provider nur unsere eigenen Zeilen (also keine), fehlt in der Praxis die Berechtigung.
            TvSnapshot(channels, watchNext, permissionMissing = channels.isEmpty() && watchNext.isEmpty() && !hasPermission())
        } catch (_: SecurityException) {
            TvSnapshot(permissionMissing = true)
        } catch (_: Exception) {
            TvSnapshot()
        }
    }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(READ_EPG) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun loadChannels(): List<AppChannel> {
        val projection = arrayOf(
            TvContractCompat.Channels._ID,
            TvContractCompat.Channels.COLUMN_PACKAGE_NAME,
            TvContractCompat.Channels.COLUMN_DISPLAY_NAME,
            TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_ID,
            TvContractCompat.Channels.COLUMN_TYPE,
            TvContractCompat.Channels.COLUMN_BROWSABLE,
        )
        val result = ArrayList<AppChannel>()
        context.contentResolver.query(TvContractCompat.Channels.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.str(TvContractCompat.Channels.COLUMN_TYPE) != TvContractCompat.Channels.TYPE_PREVIEW) continue
                val id = c.long(TvContractCompat.Channels._ID)
                val pkg = c.str(TvContractCompat.Channels.COLUMN_PACKAGE_NAME).orEmpty()
                val programs = loadPrograms(TvContractCompat.buildPreviewProgramsUriForChannel(id), watchNext = false)
                if (programs.isEmpty()) continue
                result += AppChannel(
                    id = id,
                    packageName = pkg,
                    name = c.str(TvContractCompat.Channels.COLUMN_DISPLAY_NAME).orEmpty(),
                    internalId = c.str(TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_ID).orEmpty(),
                    programs = programs,
                )
            }
        }
        return result
    }

    private fun loadPrograms(uri: Uri, watchNext: Boolean): List<ChannelProgram> {
        val list = ArrayList<ChannelProgram>()
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val duration = c.long(TvContractCompat.PreviewPrograms.COLUMN_DURATION_MILLIS)
                val position = c.long(TvContractCompat.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS)
                val title = c.str(TvContractCompat.PreviewPrograms.COLUMN_TITLE).orEmpty()
                if (title.isBlank() && c.str(TvContractCompat.PreviewPrograms.COLUMN_POSTER_ART_URI) == null) continue
                list += ChannelProgram(
                    id = c.long(TvContractCompat.PreviewPrograms._ID),
                    packageName = c.str(TvContractCompat.PreviewPrograms.COLUMN_PACKAGE_NAME).orEmpty(),
                    title = title,
                    subtitle = c.str(TvContractCompat.PreviewPrograms.COLUMN_EPISODE_TITLE).orEmpty(),
                    description = c.str(TvContractCompat.PreviewPrograms.COLUMN_SHORT_DESCRIPTION).orEmpty(),
                    posterUri = c.str(TvContractCompat.PreviewPrograms.COLUMN_POSTER_ART_URI)
                        ?: c.str(TvContractCompat.PreviewPrograms.COLUMN_THUMBNAIL_URI),
                    aspect = aspectOf(c.int(TvContractCompat.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO)),
                    intentUri = c.str(TvContractCompat.PreviewPrograms.COLUMN_INTENT_URI),
                    live = c.int(TvContractCompat.PreviewPrograms.COLUMN_LIVE) == 1,
                    progress = if (duration > 0 && position > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null,
                    lastEngagement = if (watchNext) c.long(TvContractCompat.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS) else 0L,
                )
            }
        }
        return list
    }

    private fun aspectOf(ratio: Int): Float = when (ratio) {
        TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9 -> 16f / 9f
        TvContractCompat.PreviewPrograms.ASPECT_RATIO_3_2 -> 3f / 2f
        TvContractCompat.PreviewPrograms.ASPECT_RATIO_4_3 -> 4f / 3f
        TvContractCompat.PreviewPrograms.ASPECT_RATIO_1_1 -> 1f
        TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3 -> 2f / 3f
        TvContractCompat.PreviewPrograms.ASPECT_RATIO_MOVIE_POSTER -> 1f / 1.441f
        else -> 16f / 9f
    }

    companion object {
        const val READ_EPG = "com.android.providers.tv.permission.READ_EPG_DATA"

        /** Macht aus der Link-Adresse eines Eintrags einen startbaren Intent. */
        fun intentOf(uri: String?): Intent? {
            if (uri.isNullOrBlank()) return null
            return runCatching {
                if (uri.startsWith("intent:")) Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                else Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            }.getOrNull()?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

private fun Cursor.idx(name: String) = getColumnIndex(name)
private fun Cursor.str(name: String): String? = idx(name).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }
private fun Cursor.long(name: String): Long = idx(name).takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) } ?: 0L
private fun Cursor.int(name: String): Int = idx(name).takeIf { it >= 0 && !isNull(it) }?.let { getInt(it) } ?: 0
