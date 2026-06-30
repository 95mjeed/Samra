package com.samra.downloader.ui.sheets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samra.downloader.AppViewModel
import com.samra.downloader.i18n.Num
import com.samra.downloader.model.DemoBooks
import com.samra.downloader.model.Sources
import com.samra.downloader.ui.Sym
import com.samra.downloader.ui.colors
import com.samra.downloader.ui.coverBrush
import com.samra.downloader.ui.screens.SheetScrim
import com.samra.downloader.ui.screens.SheetSurface
import com.samra.downloader.ui.str
import java.io.File

@Composable
fun SheetHost(vm: AppViewModel) {
    if (!vm.sheetOpen) return
    when (vm.sheetMode) {
        "detail" -> DetailSheet(vm)
        "series" -> SeriesSheet(vm)
        "login" -> AuthSheet(vm, cookies = false)
        "cookies" -> AuthSheet(vm, cookies = true)
        "manage" -> ManageSheet(vm)
    }
}

@Composable
private fun DetailSheet(vm: AppViewModel) {
    val c = colors(); val t = str(); val lang = vm.lang
    val ctx = LocalContext.current
    val entry = vm.library.getOrNull(vm.detailBook) ?: return
    val b = entry.book
    val m = entry.meta
    val dash = "—"
    SheetScrim({ vm.closeSheet() }) {
        SheetSurface(onDismiss = { vm.closeSheet() }) {
            // Header — tap the cover to review it full-screen.
            Row {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.BottomEnd) {
                    com.samra.downloader.ui.BookCover(
                        entry, lang,
                        Modifier.width(82.dp).clip(RoundedCornerShape(13.dp)).clickable { vm.openCover(vm.detailBook) },
                        finished = vm.isFinished(entry),
                    )
                    androidx.compose.foundation.layout.Box(
                        Modifier.padding(4.dp).size(20.dp).clip(RoundedCornerShape(7.dp)).background(c.text.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) { Sym("search", c.bg, 13.dp) }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.title(lang), color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (b.author(lang).isNotBlank()) Text(b.author(lang), color = c.text2, fontSize = 13.sp)
                    if (b.narrator(lang).isNotBlank()) Text("${t.narratedBy} ${b.narrator(lang)}", color = c.text3, fontSize = 11.5.sp)
                }
            }
            Spacer(Modifier.height(16.dp))

            // Files — side by side
            com.samra.downloader.ui.screens.SectionLabel(t.filesTitle)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (entry.hasAudio) AssetCard(
                    Modifier.weight(1f), t.assetAudio, "headphones", b.fmt, Num.size(b.mb, lang),
                    "${Num.dur(b.h, b.m, lang)} · ${Num.ar(b.ch, lang)} ${t.chaptersU}",
                    "play_arrow", t.playAudio, open = { vm.openPlayer(vm.detailBook) },
                    send = { shareFile(ctx, entry.firstAudio, "audio/*", t.shareTitle, lang) { vm.toast = it } },
                )
                b.ep?.let { ep ->
                    AssetCard(
                        Modifier.weight(1f), t.assetEbook, "menu_book", ep.fmt, Num.size(ep.mb, lang),
                        if (ep.pages > 0) "${Num.ar(ep.pages, lang)} ${t.pagesU}" else "",
                        "chrome_reader_mode", t.readEbook, open = { vm.openReader(vm.detailBook) },
                        send = {
                            val p = entry.ebookPath
                            val mime = if (p?.endsWith(".pdf", true) == true) "application/pdf" else "application/epub+zip"
                            shareFile(ctx, p, mime, t.shareTitle, lang) { vm.toast = it }
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Folder path
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.input).border(1.dp, c.line2, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    Sym("folder", c.accent, 17.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(m.folder, color = c.text2, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(16.dp))

            // Summary / description
            if (m.description.isNotBlank()) {
                com.samra.downloader.ui.screens.SectionLabel(t.mSummary)
                Spacer(Modifier.height(10.dp))
                val expanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card)
                        .border(1.dp, c.line, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        m.description, color = c.text2, fontSize = 13.sp, lineHeight = 20.sp,
                        maxLines = if (expanded.value) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis,
                    )
                    if (m.description.length > 220) {
                        Spacer(Modifier.height(8.dp))
                        // Only the "show more/less" is tappable — a small bounded pill,
                        // so there's no full-card "circle" ripple over the description.
                        androidx.compose.foundation.layout.Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .clickable { expanded.value = !expanded.value }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(if (expanded.value) t.showLess else t.showMore, color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // About this book — metadata grid
            com.samra.downloader.ui.screens.SectionLabel(t.metaTitle)
            Spacer(Modifier.height(10.dp))
            val cells = listOf(
                Triple("menu_book", t.mGenre, m.genre.ifBlank { dash }),
                Triple("business", t.mPublisher, m.publisher.ifBlank { dash }),
                Triple("calendar_today", t.mYear, if (m.year.isNotBlank()) Num.ar(m.year, lang) else dash),
                Triple("translate", t.mLang, m.language.ifBlank { dash }),
                Triple("record_voice_over", t.mNarrator, b.narrator(lang).ifBlank { dash }),
                Triple("schedule", t.mDuration, Num.dur(b.h, b.m, lang)),
                Triple("graphic_eq", t.mQuality, "${b.fmt}" + if (m.bitrate > 0) " · ${Num.ar(m.bitrate, lang)} kbps" else ""),
                Triple("cloud_download", t.mSource, m.source.ifBlank { dash }.replaceFirstChar { it.uppercase() }),
            )
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                cells.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        row.forEach { (icon, label, value) -> MetaCell(Modifier.weight(1f), icon, label, value) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            // Mark as read / unread
            val ar = lang == com.samra.downloader.i18n.Lang.AR
            val isRead = vm.isFinished(entry)
            OutlinedButton(
                onClick = { vm.toggleFinished(entry) },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
            ) {
                Sym(if (isRead) "check_circle" else "radio_button_unchecked", if (isRead) c.success else c.text2, 19.dp)
                Spacer(Modifier.width(7.dp))
                Text(
                    if (isRead) (if (ar) "تحديد كغير مقروء" else "Mark as unread")
                    else (if (ar) "تحديد كمقروء" else "Mark as read"),
                    color = c.text, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))

            // Delete
            OutlinedButton(
                onClick = { val e = entry; vm.closeSheet(); vm.deleteBook(e); vm.toast = "${t.deleteShort}: ${b.title(lang)}" },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
            ) { Sym("delete", c.error, 19.dp); Spacer(Modifier.width(7.dp)); Text(t.deleteShort, color = c.error, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SeriesSheet(vm: AppViewModel) {
    val c = colors(); val t = str(); val lang = vm.lang
    val members = vm.seriesMembers.toList().mapNotNull { i -> vm.library.getOrNull(i)?.let { i to it } }
    SheetScrim({ vm.closeSheet() }) {
        SheetSurface(onDismiss = { vm.closeSheet() }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(c.accentSoft),
                    contentAlignment = Alignment.Center,
                ) { Sym("auto_stories", c.accent, 22.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(vm.seriesName, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (lang == com.samra.downloader.i18n.Lang.AR) "${Num.ar(members.size, lang)} أجزاء" else "${members.size} parts",
                        color = c.text2, fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                members.forEach { (i, e) ->
                    val b = e.book
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card)
                            .border(1.dp, c.line, RoundedCornerShape(14.dp))
                            .clickable { vm.openDetail(i, fromSeries = true) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.samra.downloader.ui.ListCover(e, lang, Modifier.size(width = 46.dp, height = 62.dp), finished = vm.isFinished(e), progress = vm.progressOf(e))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.title(lang), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                com.samra.downloader.ui.screens.Chip(b.fmt, c.accentSoft, c.accent)
                                Text(Num.dur(b.h, b.m, lang), color = c.text3, fontSize = 11.sp)
                            }
                        }
                        Sym("chevron_right", c.text3, 20.dp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AssetCard(
    modifier: Modifier, label: String, icon: String, fmt: String, size: String, detail: String,
    openIcon: String, openLabel: String, open: () -> Unit, send: () -> Unit,
) {
    val c = colors()
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(c.card).border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(13.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) { Sym(icon, c.accent, 21.dp) }
            androidx.compose.foundation.layout.Box(Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).background(c.card2).border(1.dp, c.line, RoundedCornerShape(17.dp)).clickable { send() }, contentAlignment = Alignment.Center) { Sym("share", c.text2, 18.dp) }
        }
        Spacer(Modifier.height(11.dp))
        Text(label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.samra.downloader.ui.screens.Chip(fmt, c.accentSoft, c.accent)
            Text(size, color = c.text3, fontSize = 11.sp)
        }
        if (detail.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(detail, color = c.text3, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { open() }, colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            shape = RoundedCornerShape(11.dp), modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 9.dp),
        ) { Sym(openIcon, c.onAccent, 18.dp); Spacer(Modifier.width(6.dp)); Text(openLabel, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold) }
    }
}

@Composable
private fun MetaCell(modifier: Modifier, icon: String, label: String, value: String) {
    val c = colors()
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(c.card).border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(horizontal = 13.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Sym(icon, c.accent, 15.dp)
            Spacer(Modifier.width(6.dp))
            Text(label.uppercase(), color = c.text3, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Opens the NATIVE Android share sheet for a downloaded file — the real system
 * chooser that lists WhatsApp, Telegram, Gmail, Drive, Bluetooth, etc. — exactly
 * like sharing from any normal app. The file is exposed via our FileProvider.
 */
private fun shareFile(
    ctx: android.content.Context,
    path: String?,
    mime: String,
    title: String,
    lang: com.samra.downloader.i18n.Lang,
    onError: (String) -> Unit,
) {
    val ar = lang == com.samra.downloader.i18n.Lang.AR
    if (path.isNullOrBlank() || !File(path).exists()) { onError(if (ar) "الملف غير موجود" else "File not found"); return }
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(path))
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = mime
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(
            android.content.Intent.createChooser(send, title).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: Exception) {
        onError(if (ar) "تعذّرت المشاركة" else (e.message ?: "Share failed"))
    }
}

@Composable
private fun AuthSheet(vm: AppViewModel, cookies: Boolean) {
    val c = colors(); val t = str()
    val ctx = LocalContext.current
    val src = Sources.find { it.id == vm.sheetSrc } ?: Sources[0]

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val dest = File(ctx.filesDir, "cookies_${src.id}.txt")
                ctx.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
                vm.cookiePath = dest.absolutePath; vm.cookiesPicked = true
            } catch (_: Exception) {}
        }
    }

    fun connect() {
        if (!vm.connected.contains(src.id)) vm.connected.add(src.id)
        vm.accounts[src.id] = vm.email.ifBlank { "you@${src.id}.com" }
        // Persist credentials for reuse: cookies always; login only if "remember".
        if (cookies) {
            com.samra.downloader.auth.CredentialStore.save(ctx, src.id, "", "", vm.cookiePath)
        } else if (vm.remember) {
            com.samra.downloader.auth.CredentialStore.save(ctx, src.id, vm.email, vm.password, null)
        }
        vm.closeSheet(); vm.email = ""; vm.password = ""; vm.cookiesPicked = false
        vm.toast = "${if (vm.lang == com.samra.downloader.i18n.Lang.AR) "تم الربط بـ" else "Connected to"} ${src.name}"
    }

    SheetScrim({ vm.closeSheet() }) {
        SheetSurface {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.samra.downloader.ui.screens.BrandTile(src, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(src.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (cookies) t.tagCookies else t.tagLogin, color = c.text2, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (!cookies) {
                Field(vm.email, { vm.email = it }, t.email, "mail", keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
                Spacer(Modifier.height(10.dp))
                Field(vm.password, { vm.password = it }, t.password, "lock", password = !vm.showPass, trailing = if (vm.showPass) "visibility_off" else "visibility", onTrailing = { vm.showPass = !vm.showPass }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Password)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.width(48.dp).height(28.dp).clip(RoundedCornerShape(16.dp)).background(if (vm.remember) c.accent else c.line2).clickable { vm.remember = !vm.remember }.padding(3.dp),
                        contentAlignment = if (vm.remember) Alignment.CenterEnd else Alignment.CenterStart,
                    ) { androidx.compose.foundation.layout.Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Color.White)) }
                    Spacer(Modifier.width(10.dp))
                    Text(t.remember, color = c.text2, fontSize = 12.sp)
                }
            } else {
                Text(t.cookiesInstr, color = c.text2, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, if (vm.cookiesPicked) c.success else c.line2, RoundedCornerShape(14.dp)).clickable { picker.launch(arrayOf("*/*")) }.padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Sym(if (vm.cookiesPicked) "task_alt" else "upload_file", if (vm.cookiesPicked) c.success else c.accent, 32.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(if (vm.cookiesPicked) t.cookiePicked else t.pickFile, color = if (vm.cookiesPicked) c.success else c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("cookies.txt", color = c.text3, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Sym("help", c.text3, 16.dp); Spacer(Modifier.width(6.dp))
                    Text(t.cookiesHelp, color = c.text3, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { connect() },
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            ) {
                Sym(if (cookies) "link" else "login", c.onAccent, 18.dp); Spacer(Modifier.width(6.dp))
                Text(if (cookies) t.connect else t.signin, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sym("verified_user", c.text3, 14.dp); Spacer(Modifier.width(6.dp))
                Text(t.privacyLong, color = c.text3, fontSize = 10.5.sp, lineHeight = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, icon: String, password: Boolean = false, trailing: String? = null, onTrailing: () -> Unit = {}, keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text) {
    val c = colors()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.input).border(1.dp, c.line2, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym(icon, c.text3, 18.dp); Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(label, color = c.text3, fontSize = 14.sp)
            BasicTextField(
                value = value, onValueChange = onChange,
                textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
                cursorBrush = SolidColor(c.accent), singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        if (trailing != null) Sym(trailing, c.text3, 18.dp, Modifier.clickable { onTrailing() })
    }
}

@Composable
private fun ManageSheet(vm: AppViewModel) {
    val c = colors(); val t = str()
    val src = Sources.find { it.id == vm.sheetSrc } ?: Sources[0]
    SheetScrim({ vm.closeSheet() }) {
        SheetSurface {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.samra.downloader.ui.screens.BrandTile(src, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column { Text(src.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold); Text(t.manageTitle, color = c.text2, fontSize = 12.sp) }
            }
            Spacer(Modifier.height(16.dp))
            Text(t.connectedAs, color = c.text3, fontSize = 12.sp)
            Text(vm.accounts[src.id] ?: "you@${src.id}.com", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("${t.lastSync}: ${t.syncVal}", color = c.text3, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { vm.openSheet(src.id, src.tag) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Sym("refresh", c.text, 18.dp); Spacer(Modifier.width(6.dp)); Text(t.reauth, color = c.text)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.connected.remove(src.id); vm.closeSheet(); vm.toast = "${if (vm.lang == com.samra.downloader.i18n.Lang.AR) "تم قطع الاتصال عن" else "Disconnected from"} ${src.name}" },
                colors = ButtonDefaults.buttonColors(containerColor = c.error.copy(alpha = 0.15f), contentColor = c.error),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            ) { Sym("close", c.error, 18.dp); Spacer(Modifier.width(6.dp)); Text(t.disconnect, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
        }
    }
}
