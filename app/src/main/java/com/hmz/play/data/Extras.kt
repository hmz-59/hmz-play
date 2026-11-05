package com.hmz.play.data

import android.content.Context
import android.media.AudioManager
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Small helpers: speed-dependent volume, weather, crash log, settings backup. */
private fun prefs(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)

// ---------------------------------------------------------------- volume by speed

object SpeedVolume {
    private var steps = 0 // steps currently added on top of the user's volume

    fun enabled(ctx: Context) = prefs(ctx).getBoolean("speed_volume", false)
    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("speed_volume", on).apply()
        if (!on) apply(ctx, 0)
    }

    /** +1 step above 70 km/h, +2 above 110 km/h, back down with hysteresis. */
    fun onSpeed(ctx: Context, kmh: Int) {
        if (!enabled(ctx)) return
        val target = when {
            kmh >= 110 -> 2
            kmh >= 70 -> 1
            kmh <= 50 -> 0
            kmh <= 95 && steps == 2 -> 1
            else -> steps
        }
        apply(ctx, target)
    }

    private fun apply(ctx: Context, target: Int) {
        if (target == steps) return
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val dir = if (target > steps) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        repeat(kotlin.math.abs(target - steps)) { runCatching { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0) } }
        steps = target
    }
}

// ---------------------------------------------------------------- weather (Open-Meteo, free, no account)

data class Weather(val tempC: Int, val code: Int, val rainSoon: Boolean)

object WeatherSource {
    private val _now = MutableStateFlow<Weather?>(null)
    val now: StateFlow<Weather?> = _now.asStateFlow()
    private var started = false

    fun start() {
        if (started) return
        started = true
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            while (isActive) {
                runCatching { fetch() }
                delay(30 * 60_000L)
            }
        }
    }

    private fun fetch() {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f".format(java.util.Locale.US, Drive.lat, Drive.lon) +
                "&current=temperature_2m,weather_code&hourly=precipitation_probability&forecast_hours=3",
        )
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val cur = j.getJSONObject("current")
        val probs = j.optJSONObject("hourly")?.optJSONArray("precipitation_probability")
        var rain = false
        if (probs != null) for (i in 0 until probs.length()) if (probs.optInt(i) >= 50) rain = true
        _now.value = Weather(Math.round(cur.getDouble("temperature_2m")).toInt(), cur.getInt("weather_code"), rain)
    }

    fun emoji(code: Int) = when (code) {
        0 -> "☀️"
        1, 2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..67, in 80..82 -> "🌧️"
        in 71..77, 85, 86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "🌤️"
    }
}

// ---------------------------------------------------------------- crash log

object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val time = java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.FRANCE).format(java.util.Date())
                file(app).writeText("$time · v${com.hmz.play.BuildConfig.VERSION_NAME}\n${e.stackTraceToString().take(4000)}")
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun last(ctx: Context): String? = runCatching { file(ctx).takeIf { it.exists() }?.readText() }.getOrNull()
    fun clear(ctx: Context) { runCatching { file(ctx).delete() } }
}

// ---------------------------------------------------------------- settings backup (survives a reinstall)

object Backup {
    private fun dir() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "HMZ Play")

    /** Saves app order, favourites, theme and wallpaper to Documents/HMZ Play on the head unit. */
    fun save(ctx: Context) {
        runCatching {
            val d = dir().apply { mkdirs() }
            val p = prefs(ctx)
            val j = JSONObject()
            listOf("app_order", "fav_home", "fav_work", "theme", "recents").forEach { k -> p.getString(k, null)?.let { j.put(k, it) } }
            j.put("speed_volume", p.getBoolean("speed_volume", false))
            j.put("speed_alert", p.getInt("speed_alert", 0))
            File(d, "reglages.json").writeText(j.toString())
            val wall = File(ctx.filesDir, "wallpaper.jpg")
            if (wall.exists()) wall.copyTo(File(d, "fond.jpg"), overwrite = true) else File(d, "fond.jpg").delete()
        }
    }

    /** On a fresh install, restores the saved settings once. */
    fun restoreIfFresh(ctx: Context) {
        val p = prefs(ctx)
        if (p.getBoolean("restored_once", false)) return
        p.edit().putBoolean("restored_once", true).apply()
        if (p.contains("app_order") || p.contains("theme")) return
        runCatching {
            val j = JSONObject(File(dir(), "reglages.json").readText())
            val e = p.edit()
            j.keys().forEach { k ->
                when (val v = j.get(k)) {
                    is Boolean -> e.putBoolean(k, v)
                    is Int -> e.putInt(k, v)
                    else -> e.putString(k, v.toString())
                }
            }
            e.apply()
            val wall = File(dir(), "fond.jpg")
            if (wall.exists()) wall.copyTo(File(ctx.filesDir, "wallpaper.jpg"), overwrite = true)
        }
    }
}
