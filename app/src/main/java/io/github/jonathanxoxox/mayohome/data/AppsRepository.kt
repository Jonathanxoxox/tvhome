package io.github.jonathanxoxox.mayohome.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Eine startbare App. [banner] ist das 16:9-Bild, das TV-Apps mitbringen; sonst gibt es nur das [icon]. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val launchIntent: Intent,
)

class AppsRepository(private val context: Context) {

    suspend fun load(): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val own = context.packageName
        val byPackage = LinkedHashMap<String, ResolveInfo>()
        // Erst die TV-Einträge, dann die normalen: so gewinnt bei Apps mit beidem der TV-Eintrag.
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(query, 0)) {
                val pkg = info.activityInfo.packageName
                if (pkg != own && pkg !in byPackage) byPackage[pkg] = info
            }
        }
        byPackage.values.mapNotNull { info -> runCatching { entry(pm, info) }.getOrNull() }
            .sortedBy { it.label.lowercase() }
    }

    private fun entry(pm: PackageManager, info: ResolveInfo): AppEntry {
        val activity = info.activityInfo
        val pkg = activity.packageName
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
            ?: Intent(Intent.ACTION_MAIN).setClassName(pkg, activity.name)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
        val icon = runCatching { activity.loadIcon(pm) }.getOrNull()
        return AppEntry(
            packageName = pkg,
            label = info.loadLabel(pm).toString(),
            banner = banner?.let { render(it, 320, 180) },
            icon = icon?.let { render(it, 144, 144) },
            launchIntent = intent,
        )
    }

    private fun render(d: Drawable, w: Int, h: Int): ImageBitmap {
        val bmp: Bitmap = createBitmap(w, h)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, w, h)
        d.draw(canvas)
        return bmp.asImageBitmap()
    }
}
