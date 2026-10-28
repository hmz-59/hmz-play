package com.hmz.play.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hmz.play.data.Split

/** Shared UI flags. */
object Ui {
    var searchOpen by mutableStateOf(false)
}

/** "Où allons-nous ?" — type a destination, Waze starts guidance (official Waze deep link). */
@Composable
fun DestinationSheet() {
    AnimatedVisibility(Ui.searchOpen, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
        val ctx = LocalContext.current
        var text by remember { mutableStateOf("") }
        var home by remember { mutableStateOf(Split.home(ctx)) }
        var work by remember { mutableStateOf(Split.work(ctx)) }
        val recents = remember { Split.recents(ctx) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

        fun go(dest: String) {
            if (dest.isBlank()) return
            Ui.searchOpen = false
            Split.navigateTo(ctx, dest)
        }

        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f))
                .clickable(remember { MutableInteractionSource() }, null) { Ui.searchOpen = false },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .animateEnterExit(enter = scaleIn(initialScale = 0.95f))
                    .padding(top = 24.dp, start = 16.dp, end = 16.dp)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(C.Panel)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .padding(18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Panel2)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = C.Sub, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (text.isEmpty()) Text("Adresse, lieu, ville…", color = C.Sub, fontSize = 20.sp)
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            textStyle = TextStyle(color = C.Text, fontSize = 20.sp),
                            cursorBrush = SolidColor(C.Blue),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { go(text) }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.Filled.Close, null, tint = C.Sub,
                        modifier = Modifier.size(26.dp).tap { if (text.isEmpty()) Ui.searchOpen = false else text = "" },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (text.isNotBlank()) {
                        PlaceRow(Icons.Filled.Search, C.Blue, "Aller à « ${text.trim()} »", "Avec Waze") { go(text) }
                        Row(Modifier.padding(start = 56.dp, top = 2.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SmallChip("Définir comme Maison") { Split.setHome(ctx, text); home = text.trim() }
                            SmallChip("Définir comme Travail") { Split.setWork(ctx, text); work = text.trim() }
                        }
                    }
                    PlaceRow(Icons.Filled.Home, C.Blue, "Maison", home ?: "Tape une adresse puis « Définir comme Maison »") {
                        home?.let { go(it) }
                    }
                    PlaceRow(Icons.Filled.Work, C.Orange, "Travail", work ?: "Tape une adresse puis « Définir comme Travail »") {
                        work?.let { go(it) }
                    }
                    recents.forEach { r ->
                        PlaceRow(Icons.Filled.History, Color(0xFF636366), r, "Récent") { go(r) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(icon: ImageVector, color: Color, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).tap(0.98f, onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphTile(icon, color, 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = C.Sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SmallChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(C.Panel2).tap(0.94f, onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = C.Text, fontSize = 14.sp)
    }
}
