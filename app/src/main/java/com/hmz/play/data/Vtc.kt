package com.hmz.play.data

import android.content.Context
import android.media.AudioManager
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One ride, from pickup (button "Course") to drop-off ("Terminer"). */
data class Ride(
    val start: Long,
    val end: Long,
    val km: Double,
    val platform: String,
    val price: Double,
    val payment: String,
    val destination: String,
) {
    val minutes get() = ((end - start) / 60_000).toInt()
    val day: LocalDate get() = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate()

    fun json() = JSONObject().put("start", start).put("end", end).put("km", km).put("platform", platform)
        .put("price", price).put("payment", payment).put("dest", destination).toString()

    companion object {
        fun parse(s: String) = runCatching {
            val j = JSONObject(s)
            Ride(j.getLong("start"), j.getLong("end"), j.getDouble("km"), j.getString("platform"),
                j.optDouble("price", 0.0), j.optString("payment"), j.optString("dest"))
        }.getOrNull()
    }
}

data class Stats(val rides: Int, val revenue: Double, val km: Double, val minutes: Int, val cash: Double) {
    val perKm get() = if (km > 0.5) revenue / km else 0.0
    val perHour get() = if (minutes > 5) revenue / (minutes / 60.0) else 0.0
}

/**
 * VTC mode. Pressing "Course" means the passenger is on board:
 *  - private screen (no message banners, calls show "Appel entrant" only),
 *  - music one notch lower, restored at the end,
 *  - chrono + km of the ride, HMZ DRIVE welcome card.
 * "Terminer" opens the summary (price, payment), saved in the ride log.
 */
object Vtc {
    val PLATFORMS = listOf("Bolt", "Heetch", "Uber", "Direct")
    val PAYMENTS = listOf("Appli", "Espèces", "CB", "Virement")
    const val SITE = "https://hmzdrive.com"
    const val SLOGAN = "Le trajet, sans y penser."

    var active by mutableStateOf(false)
        private set
    var startedAt by mutableStateOf(0L)
        private set
    var platform by mutableStateOf("Bolt")
        private set
    var destination by mutableStateOf("")
        private set
    /** Set when a ride just ended and waits for its price. */
    var pending by mutableStateOf<Ride?>(null)
        private set
    var showWelcome by mutableStateOf(false)

    private var startKm = 0.0
    private var volumeSteps = 0
    private var app: Context? = null

    private fun file(ctx: Context) = File(ctx.filesDir, "rides.jsonl")
    private fun p(ctx: Context) = ctx.getSharedPreferences("vtc", Context.MODE_PRIVATE)

    fun lastPlatform(ctx: Context) = p(ctx).getString("platform", "Bolt")!!
    fun goal(ctx: Context) = p(ctx).getInt("goal", 150)
    fun setGoal(ctx: Context, v: Int) = p(ctx).edit().putInt("goal", v).apply()

    fun start(ctx: Context, plat: String, dest: String) {
        app = ctx.applicationContext
        platform = plat
        destination = dest.trim()
        startedAt = System.currentTimeMillis()
        startKm = Drive.totalKm(ctx)
        active = true
        showWelcome = true
        p(ctx).edit().putString("platform", plat).apply()
        // music a notch lower for the passenger
        val am = ctx.getSystemService(AudioManager::class.java)
        volumeSteps = 0
        if (am != null && am.getStreamVolume(AudioManager.STREAM_MUSIC) > 3) {
            runCatching { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0) }
            volumeSteps = 1
        }
        if (destination.isNotBlank()) Split.navigateTo(ctx, destination)
    }

    fun km(ctx: Context) = (Drive.totalKm(ctx) - startKm).coerceAtLeast(0.0)

    fun end(ctx: Context) {
        if (!active) return
        val am = ctx.getSystemService(AudioManager::class.java)
        repeat(volumeSteps) { runCatching { am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0) } }
        volumeSteps = 0
        pending = Ride(startedAt, System.currentTimeMillis(), km(ctx), platform, 0.0, if (platform == "Direct") "Espèces" else "Appli", destination)
        active = false
        showWelcome = false
    }

    fun save(ctx: Context, ride: Ride) {
        runCatching { file(ctx).appendText(ride.json() + "\n") }
        pending = null
    }

    fun discardPending() { pending = null }

    fun rides(ctx: Context): List<Ride> =
        runCatching { file(ctx).readLines().mapNotNull { Ride.parse(it) } }.getOrDefault(emptyList()).sortedByDescending { it.start }

    fun stats(list: List<Ride>) = Stats(
        list.size, list.sumOf { it.price }, list.sumOf { it.km }, list.sumOf { it.minutes },
        list.filter { it.payment == "Espèces" }.sumOf { it.price },
    )

    fun today(ctx: Context) = rides(ctx).filter { it.day == LocalDate.now() }
    fun week(ctx: Context) = rides(ctx).filter { it.day.isAfter(LocalDate.now().minusDays(7)) }
    fun month(ctx: Context) = rides(ctx).filter { it.day.month == LocalDate.now().month && it.day.year == LocalDate.now().year }

    /** CSV for the month (URSSAF / bookkeeping), in Documents/HMZ Play on the head unit. */
    fun exportMonth(ctx: Context): File? = runCatching {
        val now = LocalDate.now()
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "HMZ Play").apply { mkdirs() }
        val f = File(dir, "courses-%d-%02d.csv".format(now.year, now.monthValue))
        val tf = java.text.SimpleDateFormat("dd/MM/yyyy;HH:mm", java.util.Locale.FRANCE)
        val lines = listOf("Date;Heure;Plateforme;Km;Minutes;Prix (€);Paiement;Destination") +
            month(ctx).sortedBy { it.start }.map {
                "${tf.format(java.util.Date(it.start))};${it.platform};${"%.1f".format(it.km)};${it.minutes};" +
                    "${"%.2f".format(it.price)};${it.payment};${it.destination.replace(";", ",")}"
            }
        f.writeText("﻿" + lines.joinToString("\n"))
        f
    }.getOrNull()
}
