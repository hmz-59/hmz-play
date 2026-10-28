package com.hmz.play.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hmz.play.data.Split
import com.hmz.play.data.MediaBridge
import com.hmz.play.data.UpdateState
import com.hmz.play.data.Updater
import com.hmz.play.BuildConfig
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hmz.play.ble.IphoneLink
import com.hmz.play.ble.LinkStatus

@Composable
fun SettingsScreen(onRequestPermissions: () -> Unit) {
    val ctx = LocalContext.current
    val status by IphoneLink.status.collectAsStateWithLifecycle()
    val tablet by IphoneLink.tabletName.collectAsStateWithLifecycle()
    val phone by IphoneLink.phoneName.collectAsStateWithLifecycle()
    val battery by IphoneLink.battery.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Text("Réglages", color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Status
            Column(Modifier.weight(1f).card().padding(20.dp)) {
                val (dot, label) = when (status) {
                    LinkStatus.CONNECTED -> C.Green to "Connecté à ${phone ?: "l'iPhone"}" + (battery?.let { " · $it %" } ?: "")
                    LinkStatus.CONNECTING -> C.Blue to "Connexion en cours…"
                    LinkStatus.WAITING -> C.Orange to "En attente de l'iPhone"
                    LinkStatus.BLUETOOTH_OFF -> C.Red to "Bluetooth désactivé"
                    LinkStatus.NEED_PERMISSION -> C.Red to "Autorisations manquantes"
                }
                Text("ÉTAT", color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(10.dp))
                    Text(label, color = C.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (status == LinkStatus.NEED_PERMISSION) PillButton("Autoriser", C.Blue, onRequestPermissions)
                    if (status == LinkStatus.BLUETOOTH_OFF) PillButton("Ouvrir Bluetooth", C.Blue) {
                        ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    PillButton("Oublier l'iPhone", C.Panel2) { IphoneLink.forgetPhone() }
                }
            }
            // Pairing guide
            Column(Modifier.weight(1.3f).card().padding(20.dp)) {
                Text("JUMELER TON IPHONE (une seule fois)", color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                val until by IphoneLink.pairingUntil.collectAsStateWithLifecycle()
                val remaining by produceState(0, until) {
                    while (true) {
                        value = ((until - android.os.SystemClock.elapsedRealtime()) / 1000).toInt().coerceAtLeast(0)
                        if (value == 0) break
                        kotlinx.coroutines.delay(1000)
                    }
                }
                if (remaining > 0) {
                    Text("Mode jumelage actif : encore $remaining s", color = C.Green, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    PillButton("1. Lancer le mode jumelage", C.Blue) { IphoneLink.startPairingMode() }
                }
                Spacer(Modifier.height(8.dp))
                Step(2, "Sur l'iPhone : Réglages → Bluetooth.")
                Step(3, "Sous « Autres appareils », touche « HMZ Play ».")
                Step(4, "Accepte le jumelage, puis « Afficher les notifications ».")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ensuite, la connexion se fait toute seule à chaque fois que tu montes dans la voiture.",
                    color = C.Sub, fontSize = 15.sp,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        // Music through the car's own Bluetooth + updates
        var musicOn by remember { mutableStateOf(MediaBridge.hasAccess(ctx)) }
        val upd by Updater.state.collectAsStateWithLifecycle()
        LifecycleResumeEffect(Unit) {
            musicOn = MediaBridge.hasAccess(ctx)
            if (musicOn) MediaBridge.start(ctx)
            onPauseOrDispose { }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f).card().padding(20.dp)) {
                Text("Musique de l'iPhone (Bluetooth voiture)", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (musicOn) "Activé : titre, pochette et commandes de la musique en cours."
                    else "Autorise « HMZ Play – musique » dans « Accès aux notifications ».",
                    color = if (musicOn) C.Green else C.Sub, fontSize = 15.sp,
                )
                if (!musicOn) {
                    Spacer(Modifier.height(10.dp))
                    PillButton("Activer", C.Blue) { MediaBridge.openAccessSettings(ctx) }
                }
            }
            Column(Modifier.weight(1f).card().padding(20.dp)) {
                Text("Version ${BuildConfig.VERSION_NAME}", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when (val u = upd) {
                        is UpdateState.Available -> "Nouvelle version ${u.version} disponible."
                        is UpdateState.Downloading -> "Téléchargement… ${u.percent} %"
                        is UpdateState.UpToDate -> "Tu as la dernière version."
                        is UpdateState.Failed -> u.reason
                        else -> "Les mises à jour se font depuis cette appli."
                    },
                    color = C.Sub, fontSize = 15.sp,
                )
                Spacer(Modifier.height(10.dp))
                if (upd is UpdateState.Available) {
                    PillButton("Installer", C.Blue) { Updater.install(ctx) }
                } else {
                    PillButton("Vérifier", C.Panel2) { Updater.check(ctx, force = true) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        // Diagnostic
        val log by IphoneLink.log.collectAsStateWithLifecycle()
        val bonded = remember(status) { IphoneLink.bondedDevices() }
        Column(Modifier.fillMaxWidth().card().padding(20.dp)) {
            Text("DIAGNOSTIC (envoie une photo de ce cadre si ça bloque)", color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (bonded.isNotEmpty()) {
                Text("Appareils déjà jumelés — touche ton iPhone pour forcer la liaison :", color = C.Text, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    bonded.take(4).forEach { (name, addr) -> PillButton(name, C.Panel2) { IphoneLink.connectTo(addr) } }
                }
                Spacer(Modifier.height(10.dp))
            } else {
                Text("Aucun appareil jumelé visible par Android.", color = C.Sub, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
            }
            if (log.isEmpty()) Text("Aucun événement pour l'instant.", color = C.Sub, fontSize = 14.sp)
            log.take(12).forEach { line ->
                Text(line, color = C.Text.copy(alpha = 0.85f), fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(Modifier.height(14.dp))
        // Split screen: Waze left, HMZ Play right
        var auto by remember { mutableStateOf(Split.autoOpen(ctx)) }
        var splitOn by remember { mutableStateOf(Split.serviceEnabled()) }
        LifecycleResumeEffect(Unit) {
            splitOn = Split.serviceEnabled()
            onPauseOrDispose { }
        }
        Column(Modifier.fillMaxWidth().card().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Barre CarPlay + Waze en écran partagé", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (splitOn) "Activé : la barre HMZ reste à gauche dans toutes les applis, Waze s'ouvre à côté de HMZ Play."
                        else "Active « HMZ Play – barre CarPlay » dans Accessibilité (une seule fois).",
                        color = if (splitOn) C.Green else C.Sub, fontSize = 15.sp,
                    )
                }
                Spacer(Modifier.width(14.dp))
                if (!splitOn) PillButton("Activer", C.Blue) { Split.openAccessibilitySettings(ctx) }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ouvrir Waze automatiquement quand l'iPhone se connecte",
                    color = C.Text, fontSize = 16.sp, modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = auto,
                    onCheckedChange = { auto = it; Split.setAutoOpen(ctx, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.Green, checkedThumbColor = Color.White),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().card().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Écran d'accueil", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Choisis HMZ Play comme appli d'accueil pour que la tablette démarre directement dessus.",
                    color = C.Sub, fontSize = 15.sp,
                )
            }
            Spacer(Modifier.width(14.dp))
            PillButton("Choisir", C.Blue) {
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(C.Blue), contentAlignment = Alignment.Center) {
            Text("$n", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = C.Text, fontSize = 17.sp)
    }
}

@Composable
fun PillButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .tap(0.94f, onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
