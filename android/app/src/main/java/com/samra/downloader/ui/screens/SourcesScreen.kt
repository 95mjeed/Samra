package com.samra.downloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samra.downloader.AppViewModel
import com.samra.downloader.auth.CredentialStore
import com.samra.downloader.i18n.Lang
import com.samra.downloader.model.Source
import com.samra.downloader.model.Sources
import com.samra.downloader.ui.Sym
import com.samra.downloader.ui.colors
import com.samra.downloader.ui.str

/**
 * Storytel-only account screen. Shows the Storytel brand + an inline credential form
 * (email / password / remember / sign-in) when signed out, and the connected account +
 * re-auth / sign-out when signed in. Replaces the old multi-source card grid.
 */
@Composable
fun SourcesScreen(vm: AppViewModel) {
    val c = colors(); val t = str()
    val ctx = LocalContext.current
    val src = Sources[0] // Storytel — the only source
    val isConnected = vm.connected.contains(src.id)
    var editing by remember { mutableStateOf(false) }
    val showForm = !isConnected || editing
    val ar = vm.lang == Lang.AR

    fun connect() {
        if (vm.email.isBlank() || vm.password.isBlank()) {
            vm.toast = if (ar) "أدخل بريدك وكلمة المرور" else "Enter your email and password"
            return
        }
        if (!vm.connected.contains(src.id)) vm.connected.add(src.id)
        vm.accounts[src.id] = vm.email.ifBlank { "you@storytel.com" }
        if (vm.remember) CredentialStore.save(ctx, src.id, vm.email, vm.password, null)
        vm.savePrefs()
        vm.password = ""; vm.showPass = false; editing = false
        vm.toast = if (ar) "تم تسجيل الدخول إلى Storytel" else "Signed in to Storytel"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(t.sourcesTitle, color = c.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(t.sourcesSub, color = c.text2, fontSize = 13.sp)

        // Brand / status header
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card)
                .border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandTile(src, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(src.name, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (isConnected) (vm.accounts[src.id] ?: "you@storytel.com")
                    else if (ar) "غير مسجّل الدخول" else "Not signed in",
                    color = if (isConnected) c.success else c.text2,
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (isConnected) Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(c.success.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Sym("check_circle", c.success, 18.dp) }
        }

        if (showForm) {
            CredField(vm.email, { vm.email = it }, t.email, "mail", keyboardType = KeyboardType.Email)
            CredField(
                vm.password, { vm.password = it }, t.password, "lock",
                password = !vm.showPass,
                trailing = if (vm.showPass) "visibility_off" else "visibility",
                onTrailing = { vm.showPass = !vm.showPass },
                keyboardType = KeyboardType.Password,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.width(48.dp).height(28.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (vm.remember) c.accent else c.line2)
                        .clickable { vm.remember = !vm.remember }.padding(3.dp),
                    contentAlignment = if (vm.remember) Alignment.CenterEnd else Alignment.CenterStart,
                ) { Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Color.White)) }
                Spacer(Modifier.width(10.dp))
                Text(t.remember, color = c.text2, fontSize = 12.sp)
            }
            Button(
                onClick = { connect() },
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            ) { Sym("login", c.onAccent, 18.dp); Spacer(Modifier.width(6.dp)); Text(t.signin, fontWeight = FontWeight.Bold) }
            if (editing) OutlinedButton(
                onClick = { editing = false; vm.password = "" },
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            ) { Text(if (ar) "إلغاء" else "Cancel", color = c.text) }
        } else {
            OutlinedButton(
                onClick = { editing = true; vm.email = vm.accounts[src.id] ?: ""; vm.password = "" },
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            ) { Sym("refresh", c.text, 18.dp); Spacer(Modifier.width(6.dp)); Text(t.reauth, color = c.text) }
            Button(
                onClick = {
                    vm.connected.remove(src.id); vm.savePrefs()
                    CredentialStore.clear(ctx, src.id) // wipe stored creds + cookie file on sign-out
                    vm.accounts.remove(src.id)
                    vm.toast = if (ar) "تم تسجيل الخروج من Storytel" else "Signed out of Storytel"
                },
                colors = ButtonDefaults.buttonColors(containerColor = c.error.copy(alpha = 0.15f), contentColor = c.error),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            ) { Sym("logout", c.error, 18.dp); Spacer(Modifier.width(6.dp)); Text(t.disconnect, fontWeight = FontWeight.Bold) }
        }

        // Privacy reassurance
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.accentSoft).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym("verified_user", c.accent, 22.dp)
            Spacer(Modifier.width(10.dp))
            Text(t.privacyShort, color = c.text2, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
fun BrandTile(src: Source, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(11.dp)).background(src.tint),
        contentAlignment = Alignment.Center,
    ) { Text(src.initials, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
}

@Composable
private fun CredField(
    value: String, onChange: (String) -> Unit, label: String, icon: String,
    password: Boolean = false, trailing: String? = null, onTrailing: () -> Unit = {},
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val c = colors()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.input)
            .border(1.dp, c.line2, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym(icon, c.text3, 18.dp); Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(label, color = c.text3, fontSize = 14.sp)
            BasicTextField(
                value = value, onValueChange = onChange,
                textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                cursorBrush = SolidColor(c.accent), singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        if (trailing != null) Sym(trailing, c.text3, 18.dp, Modifier.clickable { onTrailing() })
    }
}
