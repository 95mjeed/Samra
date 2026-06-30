package com.samra.downloader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.samra.downloader.AppViewModel
import com.samra.downloader.i18n.Num
import com.samra.downloader.model.chapterTitles
import com.samra.downloader.ui.Sym
import com.samra.downloader.ui.colors
import com.samra.downloader.ui.str

/** Dimmed scrim with a bottom-docked sheet that slides up on appear. */
@Composable
fun SheetScrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    // Fade the scrim in and slide the sheet up the first time it composes.
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    val scrim by animateFloatAsState(
        if (appear.targetState) 0.5f else 0f,
        animationSpec = tween(com.samra.downloader.ui.Motion.Med),
        label = "scrim",
    )
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
    ) {
        Box(Modifier.align(Alignment.BottomCenter)) {
            AnimatedVisibility(
                visibleState = appear,
                enter = slideInVertically(
                    tween(com.samra.downloader.ui.Motion.Slow, easing = com.samra.downloader.ui.Motion.Emphasized),
                ) { it } + fadeIn(tween(com.samra.downloader.ui.Motion.Med)),
            ) { content() }
        }
    }
}

@Composable
fun SheetSurface(
    bg: Color = colors().surface,
    onDismiss: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxH = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.90f).dp
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val thresholdPx = with(LocalDensity.current) { 130.dp.toPx() }
    Column(
        Modifier.fillMaxWidth().heightIn(max = maxH)
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .imePadding()
            .navigationBarsPadding(),
    ) {
        // Grab handle — swipe it down to dismiss. A fast flick dismisses even
        // before the distance threshold (velocity), otherwise it springs back.
        val tracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }
        val handleDrag = if (onDismiss != null) Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { tracker.resetTracking() },
                onVerticalDrag = { change, dy ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    scope.launch { offsetY.snapTo((offsetY.value + dy).coerceAtLeast(0f)) }
                },
                onDragEnd = {
                    val v = tracker.calculateVelocity().y
                    if (offsetY.value > thresholdPx || v > 1400f) onDismiss()
                    else scope.launch { offsetY.animateTo(0f, com.samra.downloader.ui.Motion.gentle()) }
                },
                onDragCancel = { scope.launch { offsetY.animateTo(0f, com.samra.downloader.ui.Motion.gentle()) } },
            )
        } else Modifier
        Box(
            Modifier.fillMaxWidth().then(handleDrag).padding(top = 9.dp, bottom = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors().line2))
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            content = content,
        )
    }
}

@Composable
fun SleepSheet(vm: AppViewModel) {
    val c = colors(); val t = str(); val lang = vm.lang
    SheetScrim({ vm.sleepSheet = false }) {
        SheetSurface {
            Text(t.sleepTimer, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(14.dp))
            val opts: List<Pair<String, () -> Unit>> = listOf(
                t.sleepOff to { vm.setSleep(null, false) },
                "${Num.ar(5, lang)} ${t.minsU}" to { vm.setSleep(5, false) },
                "${Num.ar(15, lang)} ${t.minsU}" to { vm.setSleep(15, false) },
                "${Num.ar(30, lang)} ${t.minsU}" to { vm.setSleep(30, false) },
                "${Num.ar(45, lang)} ${t.minsU}" to { vm.setSleep(45, false) },
                t.endOfChapter to { vm.setSleep(null, true) },
            )
            val selectedIdx = when {
                vm.sleepIsChapter -> 5
                vm.sleepMinutes == null -> 0
                else -> listOf(5, 15, 30, 45).indexOf(vm.sleepMinutes) + 1
            }
            opts.chunked(2).forEachIndexed { rowIdx, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    row.forEachIndexed { colIdx, (lbl, act) ->
                        val idx = rowIdx * 2 + colIdx
                        val sel = idx == selectedIdx
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (sel) c.accent else c.card2).clickable { act() }.padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(lbl, color = if (sel) c.onAccent else c.text, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun BookmarksSheet(vm: AppViewModel) {
    val c = colors(); val t = str(); val lang = vm.lang
    SheetScrim({ vm.bmSheet = false }) {
        SheetSurface {
            Text(t.bookmarksT, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            if (vm.pBookmarks.isEmpty()) {
                Text(t.noBm, color = c.text2, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    vm.pBookmarks.toList().forEach { bm ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.card).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Sym("bookmark", c.accent, 18.dp); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f).clickable {
                                com.samra.downloader.playback.PlaybackController.seekGlobal(bm); vm.bmSheet = false
                            }) {
                                Text(Num.hms(bm, lang), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("${t.chapterU} ${Num.ar(vm.curChapter(bm) + 1, lang)}", color = c.text2, fontSize = 12.sp)
                            }
                            Sym("close", c.text3, 18.dp, Modifier.clickable { vm.removeBookmark(bm) })
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
