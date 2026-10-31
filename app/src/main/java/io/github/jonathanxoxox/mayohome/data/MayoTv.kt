package io.github.jonathanxoxox.mayohome.data

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

data class MayoCategory(val id: Long, val name: String)

/** Ein Sender aus der Playlist in Mayo TV, mit laufender Sendung und dem Link zum Abspielen. */
data class MayoChannel(
    val id: Long,
    val name: String,
    val logo: String?,
    val nowTitle: String?,
    val nowStart: Long,
    val nowEnd: Long,
    val link: String,
) {
    fun progressAt(now: Long): Float? =
        if (nowEnd > nowStart && now in nowStart until nowEnd) (now - nowStart).toFloat() / (nowEnd - nowStart) else null

    fun intent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        .setPackage(TvSnapshot.MAYO_TV)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

data class MayoSnapshot(
    val available: Boolean = false,
    val categories: List<MayoCategory> = emptyList(),
    val category: List<MayoChannel> = emptyList(),
    val recent: List<MayoChannel> = emptyList(),
)

/**
 * Liest Kategorien, Sender und zuletzt gesehene Sender aus Mayo TV (dessen MayoHomeProvider).
 * Ältere Mayo-TV-Versionen haben die Schnittstelle nicht; dann ist [MayoSnapshot.available] false.
 */
class MayoTvRepository(private val context: Context) {

    @Volatile var lastError: String? = null
        private set

    suspend fun load(categoryId: Long): MayoSnapshot = withContext(Dispatchers.IO) {
        try {
            val categories = query("categories") { c ->
                MayoCategory(c.getLong(c.getColumnIndexOrThrow("id")), c.getString(c.getColumnIndexOrThrow("name")).orEmpty())
            } ?: run {
                lastError = "Mayo TV antwortet nicht (zu alte Version oder nicht installiert)."
                return@withContext MayoSnapshot()
            }
            val category = if (categoryId >= 0) query("channels?category=$categoryId&limit=60", ::channel).orEmpty() else emptyList()
            val recent = query("recent?limit=20", ::channel).orEmpty()
            lastError = null
            MayoSnapshot(true, categories, category, recent)
        } catch (e: Exception) {
            lastError = "Mayo TV: ${e.javaClass.simpleName} ${e.message.orEmpty()}"
            MayoSnapshot()
        }
    }

    private fun <T> query(path: String, row: (Cursor) -> T): List<T>? {
        val uri = Uri.parse("content://$AUTHORITY/$path")
        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        return cursor.use { c -> buildList { while (c.moveToNext()) add(row(c)) } }
    }

    private fun channel(c: Cursor): MayoChannel {
        fun str(name: String) = c.getColumnIndex(name).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getString(it) }
        fun long(name: String) = c.getColumnIndex(name).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) } ?: 0L
        return MayoChannel(
            id = long("id"),
            name = str("name").orEmpty(),
            logo = str("logo"),
            nowTitle = str("now_title"),
            nowStart = long("now_start"),
            nowEnd = long("now_end"),
            link = str("link").orEmpty(),
        )
    }

    companion object {
        const val AUTHORITY = "${TvSnapshot.MAYO_TV}.home"
    }
}

/** Macht Sendernamen aus Playlists und aus dem MagentaTV-Katalog vergleichbar. */
object ChannelNames {
    private val prefix = Regex("""^\s*(\[[^\]]*]|\|[^|]*\||[a-z]{2,3}\s*[:|]|[a-z]{2,3}\s+-\s+)\s*""")
    private val suffix = Regex("""(\s|^)(uhd|fhd|hd|sd|4k|hevc|h265|raw|backup|ᴴᴰ|ᵁᴴᴰ)(?=\s|$)""")

    fun key(name: String): String {
        var s = Normalizer.normalize(name.lowercase(Locale.GERMAN), Normalizer.Form.NFKC)
        repeat(2) { s = s.replace(prefix, "") }
        s = s.replace(suffix, " ")
        s = s.replace("&", "und").replace("ü", "u").replace("ö", "o").replace("ä", "a").replace("ß", "ss")
        return s.filter { it.isLetterOrDigit() }
    }
}
