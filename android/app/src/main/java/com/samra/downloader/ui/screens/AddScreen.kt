package com.samra.downloader.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samra.downloader.AppViewModel
import com.samra.downloader.DownloadController
import com.samra.downloader.DownloadService
import com.samra.downloader.i18n.Lang
import com.samra.downloader.i18n.Num
import com.samra.downloader.ui.Sym
import com.samra.downloader.ui.colors
import com.samra.downloader.ui.str
import java.io.File

@Composable
fun AddScreen(vm: AppViewModel) {
    val c = colors(); val t = str(); val lang = vm.lang
    val ctx = LocalContext.current

    val running by DownloadController.running.collectAsState()
    val progress by DownloadController.progress.collectAsState()
    val currentBook by DownloadController.currentBook.collectAsState()
    val log by DownloadController.log.collectAsState()
    val queue by DownloadController.queue.collectAsState()

    // distinct(): if the same link is pasted twice, download it only once —
    // otherwise two runs write the same output file and can corrupt it (0-byte).
    val links = vm.paste.split("\n", " ", ",").map { it.trim() }.filter { it.startsWith("http") }.distinct()

    fun requestAllFiles() {
        try {
            ctx.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    android.net.Uri.parse("package:${ctx.packageName}"))
            )
        } catch (_: Exception) {
            try { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } catch (_: Exception) {}
        }
    }

    fun startDownload() {
        if (links.isEmpty() || running) return
        val out = com.samra.downloader.Storage.outputDir(ctx).absolutePath
        val ffmpeg = File(ctx.applicationInfo.nativeLibraryDir, "libffmpeg.so").absolutePath
        val creds = com.samra.downloader.auth.CredentialStore.allJson(ctx, vm.connected.toList())
        DownloadService.start(
            context = ctx,
            urls = ArrayList(links),
            username = vm.email,
            password = vm.password,
            cookie = vm.cookiePath,
            output = out,
            format = vm.format,
            combine = vm.combine,
            library = "",
            ffmpeg = if (File(ffmpeg).exists()) ffmpeg else "",
            creds = creds,
        )
        vm.paste = ""
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(t.addTitle, color = c.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(t.addSub, color = c.text2, fontSize = 13.sp)

        if (android.os.Build.VERSION.SDK_INT >= 30 && !com.samra.downloader.Storage.canWritePublic()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.accentSoft).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sym("folder", c.accent, 20.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (lang == Lang.AR) "اسمح بالوصول لكل الملفات لحفظ الكتب في مجلد التنزيلات/Samra"
                    else "Allow All-files access to save books to Download/Samra",
                    color = c.text2, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { requestAllFiles() },
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                    shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) { Text(if (lang == Lang.AR) "السماح" else "Allow", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }

        // Paste area
        Box(
            Modifier.fillMaxWidth().heightIn(min = 92.dp).clip(RoundedCornerShape(16.dp)).background(c.input)
                .border(1.dp, c.line2, RoundedCornerShape(16.dp)).padding(14.dp),
        ) {
            if (vm.paste.isEmpty()) Text(
                if (lang == Lang.AR) "الصق رابطاً واحداً أو أكثر هنا…" else "Paste one or more book links here…",
                color = c.text3, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
            )
            BasicTextField(
                value = vm.paste, onValueChange = { vm.paste = it },
                textStyle = TextStyle(color = c.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
            )
        }
        if (links.isNotEmpty()) Text("${Num.ar(links.size, lang)} ${t.detectedL}", color = c.accent, fontSize = 12.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    val text = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
                    if (!text.isNullOrBlank()) {
                        vm.paste = if (vm.paste.isBlank()) text else vm.paste.trimEnd() + "\n" + text
                    } else {
                        vm.toast = if (lang == Lang.AR) "الحافظة فارغة" else "Clipboard is empty"
                    }
                },
                enabled = !running,
                shape = RoundedCornerShape(12.dp),
            ) {
                Sym("content_paste", c.accent, 18.dp); Spacer(Modifier.width(6.dp)); Text(t.pasteBtn, color = c.text)
            }
            Button(
                onClick = { startDownload() }, enabled = links.isNotEmpty() && !running,
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f),
            ) { Sym("download", c.onAccent, 18.dp); Spacer(Modifier.width(6.dp)); Text(t.addBtn, fontWeight = FontWeight.Bold) }
        }

        // Overall progress (while running)
        if (running) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(15.dp)).padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.overall, color = c.text2, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { DownloadService.cancel(ctx) }, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                        Sym("close", c.text2, 16.dp); Spacer(Modifier.width(4.dp)); Text(t.qPaused, color = c.text2, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress }, color = c.info, trackColor = c.line, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                if (currentBook.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(currentBook, color = c.text2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }

        if (queue.isNotEmpty()) {
            Text(t.queueTitle, color = c.text3, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            queue.forEach { item -> QueueRow(item) }
        } else if (!running) {
            // Compact empty state — flows in the scroll, no forced gap
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) { Sym("download", c.accent, 38.dp) }
                Spacer(Modifier.height(12.dp))
                Text(t.queueEmpty, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text(t.queueEmptySub, color = c.text2, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.widthIn(max = 260.dp))
            }
        }

        // Console drawer
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card)
                .border(1.dp, c.line, RoundedCornerShape(12.dp)).clickable { vm.consoleOpen = !vm.consoleOpen }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym("downloading", c.text2, 16.dp); Spacer(Modifier.width(8.dp))
            Text(t.console, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Sym(if (vm.consoleOpen) "expand_less" else "expand_more", c.text2, 18.dp)
        }
        if (vm.consoleOpen) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(12.dp)).background(c.input).padding(10.dp),
            ) {
                if (log.isEmpty()) Text("[samra] idle", color = c.text3, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                log.forEach { line ->
                    val col = when {
                        line.startsWith("Error") || line.contains("✗") -> c.error
                        line.contains("Done") || line.contains("saved") || line.contains("✓") -> c.success
                        line.startsWith("[") || line.startsWith("↓") -> c.info
                        else -> c.text2
                    }
                    Text(line, color = col, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun QueueRow(item: DownloadController.QItem) {
    val c = colors(); val t = str()
    val (col, icon, label) = when (item.status) {
        "downloading" -> Triple(c.info, "downloading", t.qDownloading)
        "done" -> Triple(c.success, "check_circle", t.qDone)
        "failed" -> Triple(c.error, "error", t.qFailed)
        else -> Triple(c.queued, "schedule", t.qQueued)
    }
    Column(
        Modifier.fillMaxWidth().animateContentSize(animationSpec = com.samra.downloader.ui.Motion.gentle())
            .clip(RoundedCornerShape(15.dp)).background(c.card)
            .border(1.dp, if (item.status == "failed") c.error else c.line, RoundedCornerShape(15.dp)).padding(11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)).background(c.card2), contentAlignment = Alignment.Center) {
                Sym(icon, col, 22.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(col.copy(alpha = 0.16f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                        Text(label, color = col, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                    if (item.status == "downloading") {
                        Spacer(Modifier.width(8.dp))
                        Text("${(item.pct * 100).toInt()}%", color = c.text3, fontSize = 11.sp)
                    }
                }
            }
        }
        if (item.status == "downloading") {
            Spacer(Modifier.height(8.dp))
            val animPct = androidx.compose.animation.core.animateFloatAsState(item.pct.coerceIn(0f, 1f), animationSpec = androidx.compose.animation.core.tween(400), label = "qPct").value
            LinearProgressIndicator(progress = { animPct }, color = col, trackColor = c.line, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)))
        }
    }
    Spacer(Modifier.height(10.dp))
}
