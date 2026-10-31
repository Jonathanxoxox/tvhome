package io.github.jonathanxoxox.mayohome

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import io.github.jonathanxoxox.mayohome.data.AppsRepository
import io.github.jonathanxoxox.mayohome.data.MagentaEpg
import io.github.jonathanxoxox.mayohome.data.MayoTvRepository
import io.github.jonathanxoxox.mayohome.data.SettingsStore
import io.github.jonathanxoxox.mayohome.data.TvChannelsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MayoHomeApp : Application(), SingletonImageLoader.Factory {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings by lazy { SettingsStore(this, scope) }
    val apps by lazy { AppsRepository(this) }
    val tv by lazy { TvChannelsRepository(this) }
    val epg by lazy { MagentaEpg(this) }
    val mayo by lazy { MayoTvRepository(this) }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).crossfade(true).build()
}
