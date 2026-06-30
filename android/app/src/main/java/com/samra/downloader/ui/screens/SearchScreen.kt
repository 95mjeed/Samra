package com.samra.downloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samra.downloader.AppViewModel
import com.samra.downloader.i18n.Lang
import com.samra.downloader.i18n.Num
import com.samra.downloader.model.LibEntry
import com.samra.downloader.ui.ListCover
import com.samra.downloader.ui.Sym
import com.samra.downloader.ui.colors
import com.samra.downloader.ui.str

@Composable
fun SearchScreen(vm: AppViewModel) {
    val c = colors(); val t = str(); val lang = vm.lang
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val term = vm.search.trim().lowercase()
    val all = vm.library.mapIndexed { i, e -> i to e }
    val results = if (term.isEmpty()) all else all.filter {
        val b = it.second.book
        (b.ar.t + " " + b.ar.a + " " + b.en.t + " " + b.en.a).lowercase().contains(term)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Sym("arrow_back", c.text, 24.dp, Modifier.clip(CircleShape).clickable { vm.go("library") })
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(c.card)
                    .border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sym("search", c.text3, 18.dp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (vm.search.isEmpty()) Text(t.searchPh, color = c.text3, fontSize = 14.sp)
                    BasicTextField(
                        value = vm.search, onValueChange = { vm.search = it },
                        textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(c.accent), singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                if (vm.search.isNotEmpty()) Sym("close", c.text3, 18.dp, Modifier.clip(CircleShape).clickable { vm.search = "" })
            }
        }

        Spacer(Modifier.height(14.dp))
        // Recent searches (idle)
        if (term.isEmpty() && vm.recentSearches.isNotEmpty()) {
            Text("${t.recentLabel}:", color = c.text3, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.recentSearches.toList().forEach { r ->
                    Box(
                        Modifier.clip(RoundedCornerShape(11.dp)).background(c.card).border(1.dp, c.line, RoundedCornerShape(11.dp))
                            .clickable { vm.search = r }.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) { Text(r, color = c.text2, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (term.isNotEmpty()) {
            Text(
                if (lang == Lang.AR) "${Num.ar(results.size, lang)} ${t.resultsLabel}" else "${results.size} ${t.resultsLabel}",
                color = c.text2, fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
        }

        if (term.isNotEmpty() && results.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(40.dp))
                Sym("search_off", c.text3, 40.dp)
                Spacer(Modifier.height(12.dp))
                Text("${t.noResults} «${vm.search}»", color = c.text3, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                results.forEach { (i, e) ->
                    ResultRow(e, lang, vm.isFinished(e)) { vm.addRecent(vm.search); vm.openDetail(i) }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun ResultRow(e: LibEntry, lang: Lang, finished: Boolean, onClick: () -> Unit) {
    val c = colors(); val b = e.book
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card)
            .border(1.dp, c.line, RoundedCornerShape(14.dp)).clickable { onClick() }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListCover(e, lang, Modifier.size(width = 46.dp, height = 62.dp), finished = finished)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(b.title(lang), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (b.author(lang).isNotBlank()) Text(b.author(lang), color = c.text2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(b.fmt, c.accentSoft, c.accent)
                b.ep?.let { Chip(it.fmt, c.card2, c.text2) }
                Text(Num.dur(b.h, b.m, lang), color = c.text3, fontSize = 11.sp)
            }
        }
        Sym("chevron_right", c.text3, 20.dp)
    }
}
