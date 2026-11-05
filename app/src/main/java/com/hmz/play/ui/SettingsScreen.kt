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
import com.hmz.play.data.WazeInstaller
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
        SoundModeCard()
        Spacer(Modifier.height(14.dp))
        GpsCard()
        Spacer(Modifier.height(14.dp))

        // Status: iPhone through the car's own Bluetooth (calls + music)
        val carPhone by com.hmz.play.ble.CarBluetooth.connected.collectAsStateWithLifecycle()
        Row(Modifier.fillMaxWidth().card().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(if (carPhone != null) C.Green else C.Orange))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (carPhone != null) "Connecté à $carPhone" else "iPhone non connecté au Bluetooth de la voiture",
                    color = C.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                )
                Text("Son, musique et appels passent par le Bluetooth de l'autoradio. Rien n'est installé sur l'iPhone.", color = C.Sub, fontSize = 15.sp)
            }
            if (status == LinkStatus.NEED_PERMISSION) PillButton("Autoriser", C.Blue, onRequestPermissions)
            else if (carPhone == null) PillButton("Bluetooth", C.Blue) {
                ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!musicOn) PillButton("Activer", C.Blue) { MediaBridge.openAccessSettings(ctx) }
                    PillButton("Pas de son ? Réactiver", C.Panel2) {
                        if (!com.hmz.play.data.AudioSource.activate(ctx)) {
                            android.widget.Toast.makeText(ctx, "Appli Bluetooth de l'autoradio introuvable : ouvre-la une fois puis réessaie.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
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
        // Waze
        val wazeStatus by WazeInstaller.status.collectAsStateWithLifecycle()
        var wazeOk by remember { mutableStateOf(WazeInstaller.wazeInstalled(ctx)) }
        LifecycleResumeEffect(Unit) {
            wazeOk = WazeInstaller.wazeInstalled(ctx)
            onPauseOrDispose { }
        }
        Row(Modifier.fillMaxWidth().card().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Waze", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (wazeOk) "Installé." else wazeStatus ?: "Version compatible avec cet autoradio : 5.9.5.4 (les plus récentes exigent Android 10).",
                    color = if (wazeOk) C.Green else C.Sub, fontSize = 15.sp,
                )
            }
            Spacer(Modifier.width(14.dp))
            if (wazeOk) {
                PillButton("Ouvrir", C.Blue) { WazeInstaller.start(ctx) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("1. Télécharger", C.Panel2) { WazeInstaller.openDownloadPage(ctx) }
                    PillButton("2. Installer Waze", C.Blue) { WazeInstaller.installDownloaded(ctx) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        // Appearance: day/night + wallpaper
        val pickWall = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent(),
        ) { uri -> if (uri != null) com.hmz.play.data.Prefs.setWallpaper(ctx, uri) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f).card().padding(20.dp)) {
                Text("Mode jour / nuit", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Auto : clair le jour, sombre au coucher du soleil.", color = C.Sub, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        com.hmz.play.data.Prefs.ThemeMode.AUTO to "Auto",
                        com.hmz.play.data.Prefs.ThemeMode.DAY to "Jour",
                        com.hmz.play.data.Prefs.ThemeMode.NIGHT to "Nuit",
                    ).forEach { (m, label) ->
                        PillButton(label, if (com.hmz.play.data.Prefs.theme == m) C.Blue else C.Panel2) {
                            com.hmz.play.data.Prefs.setThemeMode(m)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).card().padding(20.dp)) {
                Text("Fond d'écran", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (com.hmz.play.data.Prefs.wallpaper != null) "Ta photo, adoucie derrière les cartes." else "Choisis une photo de l'autoradio.",
                    color = C.Sub, fontSize = 15.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Choisir", C.Blue) { pickWall.launch("image/*") }
                    if (com.hmz.play.data.Prefs.wallpaper != null) PillButton("Retirer", C.Panel2) {
                        com.hmz.play.data.Prefs.setWallpaper(ctx, null)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        // Driving: volume by speed, speed alert, history
        var speedVol by remember { mutableStateOf(com.hmz.play.data.SpeedVolume.enabled(ctx)) }
        var alert by remember { mutableStateOf(com.hmz.play.data.Drive.speedAlert(ctx)) }
        val history = remember { com.hmz.play.data.Drive.history(ctx) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f).card().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Volume selon la vitesse", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("Monte un peu au-dessus de 70 et 110 km/h.", color = C.Sub, fontSize = 15.sp)
                    }
                    Switch(
                        checked = speedVol,
                        onCheckedChange = { speedVol = it; com.hmz.play.data.SpeedVolume.setEnabled(ctx, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = C.Green, checkedThumbColor = Color.White),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("Rappel de vitesse (compteur en orange)", color = C.Text, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Off", 90 to "90", 110 to "110", 130 to "130").forEach { (v, label) ->
                        PillButton(label, if (alert == v) C.Blue else C.Panel2) {
                            alert = v
                            com.hmz.play.data.Drive.setSpeedAlert(ctx, v)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).card().padding(20.dp)) {
                Text("Historique de conduite", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                if (history.isEmpty()) Text("Les trajets apparaîtront ici.", color = C.Sub, fontSize = 15.sp)
                val df = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.FRANCE)
                history.take(7).forEach { (day, km, sec) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(day.format(df), color = C.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text("%.1f km".format(km), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(14.dp))
                        Text("${sec / 3600} h ${"%02d".format(sec / 60 % 60)}", color = C.Sub, fontSize = 15.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        // Maintenance + speed-limit beep
        var odoText by remember { mutableStateOf(com.hmz.play.data.Maintenance.odometer(ctx)?.toString() ?: "") }
        var maintItems by remember { mutableStateOf(com.hmz.play.data.Maintenance.items(ctx)) }
        var beep by remember { mutableStateOf(com.hmz.play.data.SpeedLimit.beepEnabled(ctx)) }
        Column(Modifier.fillMaxWidth().card().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bip si je dépasse la limitation", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Limitation de la route d'après OpenStreetMap, affichée à côté du compteur.", color = C.Sub, fontSize = 15.sp)
                }
                Switch(
                    checked = beep,
                    onCheckedChange = { beep = it; com.hmz.play.data.SpeedLimit.setBeep(ctx, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.Green, checkedThumbColor = Color.White),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Entretien", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Indique le kilométrage du compteur une fois, HMZ Play ajoute ensuite les km parcourus.", color = C.Sub, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicTextField(
                    value = odoText,
                    onValueChange = { v -> odoText = v.filter { it.isDigit() }.take(7) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = C.Text, fontSize = 20.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(C.Blue),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.width(160.dp).clip(RoundedCornerShape(12.dp)).background(C.Panel2).padding(12.dp),
                )
                Text("  km", color = C.Sub, fontSize = 17.sp)
                Spacer(Modifier.width(12.dp))
                PillButton("Enregistrer", C.Blue) {
                    odoText.toIntOrNull()?.let { com.hmz.play.data.Maintenance.setOdometer(ctx, it); maintItems = com.hmz.play.data.Maintenance.items(ctx) }
                }
            }
            maintItems.forEach { m ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.label, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        val parts = listOfNotNull(
                            m.dueInKm?.let { if (it <= 0) "à faire maintenant" else "dans $it km" },
                            m.dueDate?.let { "avant le ${it.dayOfMonth}/${it.monthValue}/${it.year}" },
                        )
                        Text(
                            if (parts.isEmpty()) "Appuie sur « Fait » après le prochain passage." else parts.joinToString(" · "),
                            color = if (m.dueInKm != null && m.dueInKm <= 500) C.Orange else C.Sub, fontSize = 14.sp,
                        )
                    }
                    PillButton("Fait", C.Panel2) { com.hmz.play.data.Maintenance.markDone(ctx, m.key); maintItems = com.hmz.play.data.Maintenance.items(ctx) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        // Doors
        val doorSrc by com.hmz.play.data.Doors.source.collectAsStateWithLifecycle()
        val doorState by com.hmz.play.data.Doors.state.collectAsStateWithLifecycle()
        Row(Modifier.fillMaxWidth().card().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            PriusView(doorState, Modifier.width(110.dp).height(150.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Portes ouvertes", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (doorSrc != null) "Reçu du boîtier CAN de la Prius ($doorSrc)."
                    else "Aucune info reçue du boîtier CAN pour l'instant. Ouvre une portière, puis envoie-moi une photo du Diagnostic.",
                    color = if (doorSrc != null) C.Green else C.Sub, fontSize = 15.sp,
                )
                Spacer(Modifier.height(10.dp))
                PillButton("Tester l'affichage", C.Blue) { com.hmz.play.data.Doors.demo() }
            }
        }

        Spacer(Modifier.height(14.dp))
        LiveKeysCard()

        Spacer(Modifier.height(14.dp))
        // Black box
        val marks by com.hmz.play.data.BlackBox.marks.collectAsStateWithLifecycle()
        Column(Modifier.fillMaxWidth().card().padding(20.dp)) {
            Text("Boîte noire", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Ta vitesse GPS est enregistrée chaque seconde (30 jours, uniquement sur l'autoradio). Après un flash, appuie sur « Flash ? » : ta vitesse des 30 secondes d'avant s'affiche ici.",
                color = C.Sub, fontSize = 15.sp,
            )
            if (marks.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Aucun moment enregistré.", color = C.Sub, fontSize = 15.sp)
            }
            val tf = java.text.SimpleDateFormat("EEE d MMM · HH:mm:ss", java.util.Locale.FRANCE)
            marks.forEach { m ->
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Panel2).padding(14.dp)) {
                    Text(tf.format(java.util.Date(m.time)), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    if (m.speedAtMark < 0) {
                        Text("Pas de signal GPS à ce moment-là.", color = C.Sub, fontSize = 15.sp)
                    } else {
                        Text(
                            "Au moment du clic : ${m.speedAtMark} km/h · max sur les 30 s d'avant : ${m.maxBefore} km/h",
                            color = C.Text, fontSize = 15.sp,
                        )
                        Text(
                            m.samples.joinToString("   ") { (s, v) -> "-${s}s : $v" },
                            color = C.Sub, fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (m.speedAtMark >= 0) PillButton("Voir l'endroit", C.Blue) {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:${m.lat},${m.lon}?q=${m.lat},${m.lon}(Flash)"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                        PillButton("Supprimer", C.Panel) { com.hmz.play.data.BlackBox.deleteMark(m.time) }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        // Diagnostic
        val log by IphoneLink.log.collectAsStateWithLifecycle()
        Column(Modifier.fillMaxWidth().card().padding(20.dp)) {
            Text("DIAGNOSTIC", color = C.Sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            val crash = remember { com.hmz.play.data.CrashLog.last(ctx) }
            if (crash != null) {
                Text("Dernier plantage (envoie-moi une photo) :", color = C.Red, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(crash.lines().take(8).joinToString("\n"), color = C.Text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(6.dp))
                PillButton("Effacer", C.Panel2) { com.hmz.play.data.CrashLog.clear(ctx) }
                Spacer(Modifier.height(10.dp))
            }
            if (log.isEmpty()) Text("Aucun événement pour l'instant.", color = C.Sub, fontSize = 14.sp)
            log.take(16).forEach { line ->
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
