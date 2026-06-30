package com.samra.downloader

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.samra.downloader.ui.Motion
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samra.downloader.playback.PlaybackController
import com.samra.downloader.ui.Sym
import com.samra.downloader.ui.bounceClick
import com.samra.downloader.ui.colors
import com.samra.downloader.ui.coverBrush
import com.samra.downloader.ui.screens.*
import com.samra.downloader.ui.sheets.SheetHost
import com.samra.downloader.ui.str
import com.samra.downloader.ui.theme.SamraTheme
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Paint the window background to match the user's saved theme choice
        // BEFORE the first Compose frame, so cold start never flashes white.
        // (Same key/default as AppViewModel.loadPrefs(): "dark" defaults to true,
        // colors match DarkColors.bg / LightColors.bg.)
        val darkPref = getSharedPreferences("samra", MODE_PRIVATE).getBoolean("dark", true)
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(
                if (darkPref) 0xFF0E0F12.toInt() else 0xFFFBF8F1.toInt()
            )
        )
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            SamraTheme(lang = vm.lang, dark = vm.dark) {
                AppShell(vm)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppShell(vm: AppViewModel) {
    val c = colors()

    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // toast auto-dismiss
    LaunchedEffect(vm.toast) {
        if (vm.toast != null) { delay(2900); vm.toast = null }
    }

    // persist settings on change
    LaunchedEffect(Unit) {
        snapshotFlow { listOf(vm.lang, vm.dark, vm.format, vm.combine, vm.skip, vm.wifi, vm.connected.toList(), vm.screen) }
            .collect { vm.savePrefs() }
    }
    // connect to the playback service early
    val appCtx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        PlaybackController.init(appCtx)
        PlaybackController.onFinished = { key ->
            vm.markFinished(key)
            vm.clearAudioPos(key)   // start fresh next time it's opened
            vm.toast = if (vm.lang == com.samra.downloader.i18n.Lang.AR) "تم إنهاء الكتاب — وُضع كمقروء" else "Finished — marked as read"
        }
    }
    // Save listening progress for the playing book so its cover shows a bar.
    LaunchedEffect(Unit) {
        while (true) {
            if (PlaybackController.hasBook) {
                val tot = PlaybackController.totalSec
                val key = PlaybackController.bookKey
                val gp = PlaybackController.globalPosition()
                if (tot > 0 && key != null) vm.setProgress(key, gp.toFloat() / tot)
                // Persist exact resume position (only while actually playing AND
                // past the very start, so a transient 0 right after open/seek can
                // never clobber a good saved position).
                if (key != null && PlaybackController.isPlaying && gp > 0) vm.saveAudioPos(key, gp)
            }
            delay(3000)
        }
    }
    // refresh library when a download finishes or we open the library
    LaunchedEffect(Unit) { DownloadController.running.collect { r -> if (!r) vm.refreshLibrary() } }
    // surface download completion as a toast
    LaunchedEffect(Unit) {
        DownloadController.event.collect { e -> if (e != null) { vm.toast = e; DownloadController.clearEvent() } }
    }
    LaunchedEffect(vm.screen) { if (vm.screen == "library") vm.refreshLibrary() }

    // System back: dismiss an open sheet first, then fall back to the library,
    // and only exit the app from the library/onboarding root.
    BackHandler(enabled = vm.anySheetOpen || (vm.screen != "library" && vm.screen != "onboarding")) {
        if (vm.anySheetOpen) vm.dismissTopSheet() else vm.go("library")
    }

    // Screens that own the bottom nav (Player/Reader are immersive — no nav).
    val navScreen = vm.screen in listOf("library", "add", "sources", "settings")
    val density = LocalDensity.current
    // Measured height of the bottom nav (incl. system nav-bar inset + mini-player).
    var navHeightDp by remember { mutableStateOf(84.dp) }
    // The content reserves whichever is taller — the nav or the keyboard — and reads
    // it in the layout pass, so it shrinks smoothly with the keyboard and NEVER
    // grows-then-shrinks (which is what made the library grid jump).
    val contentInsets = if (navScreen)
        WindowInsets.ime.union(WindowInsets(bottom = navHeightDp))
    else
        WindowInsets.ime.union(WindowInsets.navigationBars)

    Box(Modifier.fillMaxSize().background(c.bg)) {
        // imePadding on the whole stack so it shrinks smoothly with the keyboard
        // (content fills exactly to the keyboard top — no blank gap). The bottom
        // nav is removed INSTANTLY when the keyboard opens (see below), so it is
        // never lifted above the keyboard and never lingers there mid-animation.
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .windowInsetsPadding(contentInsets)
        ) {
                AnimatedContent(
                    targetState = vm.screen,
                    transitionSpec = {
                        val immersive = setOf("player", "reader")
                        when {
                            // Opening the Player/Reader: rise up from the bottom
                            // (like a now-playing sheet) over the dimming page.
                            targetState in immersive ->
                                (slideInVertically(tween(Motion.Slow, easing = Motion.Emphasized)) { it } +
                                    fadeIn(tween(Motion.Med)))
                                    .togetherWith(
                                        fadeOut(tween(Motion.Med, easing = Motion.Standard)) +
                                            scaleOut(targetScale = 0.94f, animationSpec = tween(Motion.Med, easing = Motion.Standard))
                                    )
                            // Leaving the Player/Reader: settle back down.
                            initialState in immersive ->
                                (fadeIn(tween(Motion.Med)) +
                                    scaleIn(initialScale = 0.94f, animationSpec = tween(Motion.Med, easing = Motion.Emphasized)))
                                    .togetherWith(
                                        slideOutVertically(tween(Motion.Slow, easing = Motion.Emphasized)) { it } +
                                            fadeOut(tween(Motion.Med, easing = Motion.Standard))
                                    )
                            // Between bottom-nav pages: gentle fade-through.
                            else ->
                                (fadeIn(tween(Motion.Med, easing = Motion.Emphasized)) +
                                    scaleIn(initialScale = 0.985f, animationSpec = tween(Motion.Med, easing = Motion.Emphasized)))
                                    .togetherWith(fadeOut(tween(Motion.Fast, easing = Motion.Standard)))
                        }
                    },
                    label = "screen",
                    modifier = Modifier.fillMaxSize(),
                ) { screen ->
                    when (screen) {
                        "onboarding" -> OnboardingScreen(vm)
                        "library" -> LibraryScreen(vm)
                        "search" -> SearchScreen(vm)
                        "add" -> AddScreen(vm)
                        "sources" -> SourcesScreen(vm)
                        "settings" -> SettingsScreen(vm)
                        "player" -> PlayerScreen(vm)
                        "reader" -> ReaderScreen(vm)
                    }
                }
            }
        // Bottom nav + mini-player, anchored to the screen bottom as an overlay.
        // The content above reserves max(navHeight, keyboardHeight), so it shrinks
        // monotonically with the keyboard — no bounce/flicker. When the keyboard
        // rises it simply draws OVER this nav. The slide is for Player/Reader only.
        AnimatedVisibility(
            visible = navScreen,
            enter = slideInVertically(tween(Motion.Med, easing = Motion.Emphasized)) { it } + fadeIn(tween(Motion.Med)),
            exit = slideOutVertically(tween(Motion.Med, easing = Motion.Standard)) { it } + fadeOut(tween(Motion.Fast)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .onSizeChanged { navHeightDp = with(density) { it.height.toDp() } },
            ) {
                AnimatedVisibility(
                    visible = PlaybackController.hasBook && vm.screen != "player",
                    enter = Motion.dockEnter(),
                    exit = Motion.dockExit(),
                ) { MiniPlayer(vm) }
                BottomNav(vm)
            }
        }

        // sheets
        SheetHost(vm)

        // Full-screen cover viewer (tap a cover in the detail popup) — shows the
        // WHOLE cover uncropped on a dark scrim; tap anywhere or ✕ to close.
        AnimatedVisibility(
            visible = vm.coverView != null,
            enter = fadeIn(tween(Motion.Med)),
            exit = fadeOut(tween(Motion.Fast)),
        ) {
            val entry = vm.coverView?.let { vm.library.getOrNull(it) }
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f))
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { vm.coverView = null }
                    .statusBarsPadding().navigationBarsPadding(),
            ) {
                if (entry != null) {
                    val art = com.samra.downloader.ui.rememberCover(entry.coverPath, entry.firstAudio, entry.ebookPath)
                    if (art != null) {
                        // Fill as much of the screen as possible (Fit keeps the whole
                        // cover, scaled to the largest size that fits the viewport).
                        androidx.compose.foundation.Image(
                            art, null,
                            Modifier.fillMaxSize().align(Alignment.Center)
                                .padding(start = 14.dp, end = 14.dp, top = 64.dp, bottom = 76.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        )
                    } else {
                        com.samra.downloader.ui.BookCover(entry, vm.lang, Modifier.fillMaxWidth(0.9f).align(Alignment.Center))
                    }
                    Text(
                        entry.book.title(vm.lang), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 28.dp, vertical = 28.dp),
                    )
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(16.dp).size(42.dp).clip(CircleShapeMini)
                        .background(Color.White.copy(alpha = 0.16f)).clickable { vm.coverView = null },
                    contentAlignment = Alignment.Center,
                ) { Sym("close", Color.White, 24.dp) }
            }
        }

        // toast
        AnimatedVisibility(
            visible = vm.toast != null,
            enter = slideInVertically(tween(Motion.Med, easing = Motion.Emphasized)) { it / 2 } + fadeIn(tween(Motion.Med)),
            exit = fadeOut(tween(Motion.Fast)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.card2)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(vm.toast ?: "", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MiniPlayer(vm: AppViewModel) {
    val c = colors()
    val idx = PlaybackController.entryIndex ?: return
    val entry = vm.library.getOrNull(idx) ?: return
    val b = entry.book
    val playing = PlaybackController.isPlaying

    var frac by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            val tot = PlaybackController.totalSec.coerceAtLeast(1)
            frac = PlaybackController.globalPosition().toFloat() / tot
            delay(1000)
        }
    }

    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card2)
                .bounceClick(scaleDown = 0.97f) { vm.openPlayer(idx) },
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(coverBrush(b.c1, b.c2))) {
                    val art = com.samra.downloader.ui.rememberCover(entry.coverPath, entry.firstAudio, entry.ebookPath)
                    if (art != null) androidx.compose.foundation.Image(art, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.title(vm.lang), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (b.author(vm.lang).isBlank()) b.fmt else b.author(vm.lang), color = c.text2, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShapeMini).background(c.accentSoft).bounceClick(scaleDown = 0.85f) { PlaybackController.toggle() },
                    contentAlignment = Alignment.Center,
                ) { Sym(if (playing) "pause" else "play_arrow", c.accent, 24.dp) }
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier.size(34.dp).clip(CircleShapeMini).bounceClick(scaleDown = 0.85f) { PlaybackController.stop() },
                    contentAlignment = Alignment.Center,
                ) { Sym("close", c.text3, 20.dp) }
            }
            val animFrac by animateFloatAsState(frac.coerceIn(0f, 1f), animationSpec = tween(1000, easing = Motion.Standard), label = "miniProgress")
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(animFrac).height(3.dp).background(c.accent))
        }
    }
}

private val CircleShapeMini = androidx.compose.foundation.shape.RoundedCornerShape(50)

@Composable
private fun BottomNav(vm: AppViewModel) {
    val c = colors(); val t = str()
    val items = listOf(
        Triple("library", "menu_book", t.navLib),
        Triple("add", "download", t.navAdd),
        Triple("sources", "hub", t.navSrc),
        Triple("settings", "settings", t.navSet),
    )
    // The surface fills the whole nav incl. the area BEHIND the system gesture/
    // nav bar (navigationBarsPadding on the Row pushes the buttons up while the
    // Column background extends down), so the bottom blends seamlessly edge-to-edge.
    Column(Modifier.fillMaxWidth().background(c.surface)) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 7.dp, bottom = 8.dp),
    ) {
        items.forEach { (key, icon, label) ->
            val active = vm.screen == key
            // Only the colors animate for the active state — the layout (slot
            // width + pill size) is FIXED, so buttons never resize/shift when
            // you switch tabs. Each item gets an equal weighted slot.
            val pillColor by animateColorAsState(if (active) c.accentSoft else Color.Transparent, Motion.snappy(), label = "navPill")
            val tint by animateColorAsState(if (active) c.accent else c.text3, tween(Motion.Med), label = "navTint")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .bounceClick(scaleDown = 0.9f) { vm.go(key) }
                    .padding(vertical = 2.dp),
            ) {
                Box(
                    Modifier
                        .width(56.dp).height(31.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(pillColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Sym(icon, tint, 22.dp)
                }
                Spacer(Modifier.height(3.dp))
                Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
    }
}
