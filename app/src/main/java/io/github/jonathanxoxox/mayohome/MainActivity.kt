package io.github.jonathanxoxox.mayohome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import io.github.jonathanxoxox.mayohome.data.TvChannelsRepository
import io.github.jonathanxoxox.mayohome.system.Launcher
import io.github.jonathanxoxox.mayohome.ui.AppsScreen
import io.github.jonathanxoxox.mayohome.ui.Clock
import io.github.jonathanxoxox.mayohome.ui.FocusCard
import io.github.jonathanxoxox.mayohome.ui.HomeViewModel
import io.github.jonathanxoxox.mayohome.ui.LocalLook
import io.github.jonathanxoxox.mayohome.ui.MayoTheme
import io.github.jonathanxoxox.mayohome.ui.Palette
import io.github.jonathanxoxox.mayohome.ui.SettingsScreen
import io.github.jonathanxoxox.mayohome.ui.StartScreen
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()

    /** Zählt Home-Tastendrücke, damit die Oberfläche zurück zum Start springt. */
    private val homePresses = MutableStateFlow(0)

    private val askTvPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshTv() }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { vm.refreshApps() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        setContent {
            val settings by vm.settings.collectAsState()
            val home by homePresses.collectAsState()
            MayoTheme(settings) {
                HomeRoot(vm, home, onRequestTvPermission = ::requestTvPermission)
            }
        }
    }

    private fun requestTvPermission() {
        if (checkSelfPermission(TvChannelsRepository.READ_EPG) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            vm.refreshTv()
        } else {
            askTvPermission.launch(TvChannelsRepository.READ_EPG)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.getBooleanExtra(Launcher.EXTRA_FROM_HOME, false)) {
            homePresses.value += 1
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }
}

@Composable
private fun HomeRoot(vm: HomeViewModel, homePresses: Int, onRequestTvPermission: () -> Unit) {
    val settings by vm.settings.collectAsState()
    var tab by remember { mutableStateOf(settings.startTab) }
    var showSettings by remember { mutableStateOf(false) }
    var heroImage by remember { mutableStateOf<String?>(null) }
    val heroFocus = remember { FocusRequester() }
    val appsFocus = remember { FocusRequester() }
    val look = LocalLook.current

    // Home-Taste: zurück auf die Startseite, Fokus aufs Poster.
    LaunchedEffect(homePresses) {
        if (homePresses > 0) {
            showSettings = false
            tab = "start"
        }
        runCatching { heroFocus.requestFocus() }
    }
    BackHandler(enabled = !showSettings) {
        if (tab != "start") tab = "start" else runCatching { heroFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(look.page)) {
        if (look.heroBackdrop && heroImage != null) {
            AsyncImage(heroImage, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.22f))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 1f to look.page)))
        }
        Column(Modifier.fillMaxSize()) {
            TopBar(
                tab = tab,
                onTab = { tab = it },
                onSettings = { showSettings = true },
                clock24 = settings.clock24,
                showDate = settings.showDate,
            )
            Crossfade(targetState = tab, label = "tab") { current ->
                when (current) {
                    "apps" -> AppsScreen(vm, settings, appsFocus)
                    else -> StartScreen(
                        vm = vm,
                        settings = settings,
                        heroFocus = heroFocus,
                        onHeroImage = { heroImage = it },
                        onOpenSettings = { showSettings = true },
                        onRequestTvPermission = onRequestTvPermission,
                    )
                }
            }
        }
        if (showSettings) {
            SettingsScreen(vm, settings, onClose = { showSettings = false }, onRequestTvPermission = onRequestTvPermission)
        }
    }
}

@Composable
private fun TopBar(tab: String, onTab: (String) -> Unit, onSettings: () -> Unit, clock24: Boolean, showDate: Boolean) {
    Box(Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 22.dp, bottom = 14.dp)) {
        Row(
            Modifier.align(Alignment.Center).background(Color(0x1FF5F7F7), RoundedCornerShape(50)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TabPill("Start", tab == "start") { onTab("start") }
            TabPill("Apps", tab == "apps") { onTab("apps") }
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FocusCard(onClick = onSettings, corner = 50.dp, scale = 1.1f, container = Color.Transparent, modifier = Modifier.size(32.dp)) {
                GearIcon(Modifier.align(Alignment.Center).size(18.dp))
            }
            Clock(clock24, showDate)
        }
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onSelect: () -> Unit) {
    FocusCard(
        onClick = onSelect,
        onFocus = { if (it) onSelect() },
        corner = 50.dp,
        scale = 1.04f,
        container = if (selected) Palette.Text else Color.Transparent,
        focusedContainer = Palette.Text,
    ) {
        Text(
            label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Color(0xFF0D1113) else Palette.Text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun GearIcon(modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2
        repeat(8) { i ->
            rotate(i * 45f, c) {
                drawRect(Palette.Text, topLeft = Offset(c.x - r * 0.14f, c.y - r), size = androidx.compose.ui.geometry.Size(r * 0.28f, r * 0.4f))
            }
        }
        drawCircle(Palette.Text, radius = r * 0.62f, center = c, style = Stroke(width = r * 0.26f))
    }
}
