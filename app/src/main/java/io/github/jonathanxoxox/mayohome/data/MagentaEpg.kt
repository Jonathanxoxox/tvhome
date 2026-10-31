package io.github.jonathanxoxox.mayohome.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Eine Sendung aus dem MagentaTV-Programm, mit dem Bild aus der TV-Zeitschrift. */
data class EpgProgram(
    val channelId: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val genres: String,
    val start: Long,
    val end: Long,
    val imageUrl: String?,
) {
    fun runningAt(now: Long) = start <= now && end > now
    fun progressAt(now: Long) = ((now - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
}

data class CatalogChannel(val id: String, val name: String)

/**
 * Programmdaten von MagentaTV, so wie sie auch die Web-App von MagentaTV lädt (dieselbe Abfrage nutzt
 * das Open-Source-Projekt iptv-org/epg). Das ist keine offizielle Schnittstelle: Sie kann sich ändern
 * und antwortet womöglich nur aus Deutschland. Fehler führen deshalb nur zu einem leeren Ergebnis.
 */
class MagentaEpg(private val context: Context) {

    private val cookies = HashMap<String, Cookie>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                synchronized(this@MagentaEpg.cookies) { cookies.forEach { this@MagentaEpg.cookies[it.name] = it } }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                synchronized(this@MagentaEpg.cookies) { this@MagentaEpg.cookies.values.toList() }
        })
        .build()
    private val authLock = Mutex()
    private var csrfToken: String? = null

    /** Letzter Fehler als kurzer Text für die Einstellungen, oder null. */
    @Volatile var lastError: String? = null
        private set

    val catalog: List<CatalogChannel> by lazy {
        runCatching {
            val text = context.assets.open("magenta_channels.json").bufferedReader().use { it.readText() }
            val array = JSONArray(text)
            List(array.length()) { i -> array.getJSONObject(i).let { CatalogChannel(it.getString("id"), it.getString("name")) } }
        }.getOrDefault(emptyList())
    }

    fun name(id: String): String = catalog.firstOrNull { it.id == id }?.name ?: id

    suspend fun load(channelIds: List<String>, from: Long, to: Long): Map<String, List<EpgProgram>> = withContext(Dispatchers.IO) {
        if (channelIds.isEmpty()) return@withContext emptyMap()
        try {
            ensureAuth()
            val result = coroutineScope {
                channelIds.distinct().map { id -> async { id to fetch(id, from, to) } }.awaitAll()
            }.toMap()
            lastError = if (result.values.all { it.isEmpty() }) "MagentaTV hat keine Sendungen geliefert." else null
            result
        } catch (e: Exception) {
            lastError = "MagentaTV nicht erreichbar: ${e.message ?: e.javaClass.simpleName}"
            csrfToken = null
            emptyMap()
        }
    }

    private suspend fun ensureAuth() = authLock.withLock {
        if (csrfToken != null) return@withLock
        val body = JSONObject()
            .put("terminalid", "00:00:00:00:00:00").put("mac", "00:00:00:00:00:00")
            .put("terminaltype", "WEBTV").put("utcEnable", 1).put("timezone", "Etc/GMT0")
            .put("userType", 3).put("terminalvendor", "Unknown")
        val request = Request.Builder()
            .url("$BASE/Authenticate?SID=firstup&T=Windows_chrome_118")
            .post(body.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            val json = JSONObject(response.body.string())
            csrfToken = json.optString("csrfToken").takeIf { it.isNotBlank() }
                ?: error("kein Token (${json.optString("retcode")})")
        }
    }

    private fun fetch(channelId: String, from: Long, to: Long): List<EpgProgram> {
        val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val body = JSONObject()
            .put("count", -1).put("isFillProgram", 1).put("offset", 0)
            .put("properties", JSONArray().put(JSONObject()
                .put("name", "playbill")
                .put("include", "endtime,genres,id,name,starttime,channelid,pictures,introduce,subName,seasonNum,subNum")))
            .put("type", 2)
            .put("begintime", fmt.format(Date(from)))
            .put("endtime", fmt.format(Date(to)))
            .put("channelid", channelId)
        val request = Request.Builder()
            .url("$BASE/PlayBillList")
            .header("X_CSRFTOKEN", csrfToken.orEmpty())
            .post(body.toString().toRequestBody(JSON))
            .build()
        return client.newCall(request).execute().use { response ->
            val json = JSONObject(response.body.string())
            val list = json.optJSONArray("playbilllist") ?: return@use emptyList()
            List(list.length()) { list.getJSONObject(it) }.mapNotNull { parse(channelId, it) }
        }
    }

    private fun parse(channelId: String, o: JSONObject): EpgProgram? {
        val start = time(o.optString("starttime")) ?: return null
        val end = time(o.optString("endtime")) ?: return null
        val pictures = o.optJSONArray("pictures")
        // Das größte Bild nehmen; die Liste enthält oft mehrere Formate.
        val image = pictures?.let { arr ->
            List(arr.length()) { arr.getJSONObject(it) }
                .maxByOrNull { pic -> pic.optJSONArray("resolution")?.optString(0)?.toIntOrNull() ?: 0 }
                ?.optString("href")
        }?.takeIf { it.isNotBlank() }
        val season = o.optString("seasonNum").takeIf { it.isNotBlank() && it != "0" }
        val episode = o.optString("subNum").takeIf { it.isNotBlank() && it != "0" }
        val subtitle = listOfNotNull(
            season?.let { "Staffel $it" }, episode?.let { "Folge $it" }, o.optString("subName").takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        return EpgProgram(
            channelId = channelId,
            title = o.optString("name"),
            subtitle = subtitle,
            description = o.optString("introduce"),
            genres = o.optString("genres"),
            start = start,
            end = end,
            imageUrl = image,
        )
    }

    /** "2024-11-05 15:37:03 UTC+01:00" */
    private fun time(text: String): Long? = runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'XXX", Locale.ROOT).parse(text)?.time
    }.getOrNull()

    private companion object {
        const val BASE = "https://api.prod.sngtv.magentatv.de/EPG/JSON"
        val JSON = "application/json".toMediaType()
    }
}
