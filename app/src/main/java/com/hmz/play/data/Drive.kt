package com.hmz.play.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Today's driving: GPS speed, kilometres and time spent moving. Resets every day. */
data class DriveDay(val speedKmh: Int = 0, val km: Double = 0.0, val movingSec: Long = 0, val hasFix: Boolean = false)

@SuppressLint("MissingPermission")
object Drive {
    private val _state = MutableStateFlow(DriveDay())
    val state: StateFlow<DriveDay> = _state.asStateFlow()

    /** Last known position, also used for sunrise/sunset. Defaults to Lille. */
    @Volatile var lat = 50.63
    @Volatile var lon = 3.06

    /** Elapsed-realtime of the last moment the car was moving (for the parked screen). */
    private val _lastMoving = MutableStateFlow(android.os.SystemClock.elapsedRealtime())
    val lastMoving: StateFlow<Long> = _lastMoving.asStateFlow()

    private var started = false
    private var last: Location? = null
    private var lastSave = 0L
    private var app: Context? = null

    private fun prefs() = app?.getSharedPreferences("drive", Context.MODE_PRIVATE)

    fun start(ctx: Context) {
        if (started) return
        val c = ctx.applicationContext
        val lm = c.getSystemService(LocationManager::class.java) ?: return
        app = c
        BlackBox.start(c)
        val p = prefs()!!
        val today = LocalDate.now().toString()
        if (p.getString("day", null) == today) {
            _state.value = DriveDay(km = p.getFloat("km", 0f).toDouble(), movingSec = p.getLong("sec", 0))
        } else {
            p.edit().putString("day", today).putFloat("km", 0f).putLong("sec", 0).apply()
        }
        lat = p.getFloat("lat", lat.toFloat()).toDouble()
        lon = p.getFloat("lon", lon.toFloat()).toDouble()
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            started = true
        }
    }

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) = onFix(loc)
        @Deprecated("Deprecated in Java") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) = Unit
        override fun onProviderEnabled(p: String) = Unit
        override fun onProviderDisabled(p: String) = Unit
    }

    private fun onFix(loc: Location) {
        lat = loc.latitude
        lon = loc.longitude
        val today = LocalDate.now().toString()
        val p = prefs()
        var s = _state.value
        if (p != null && p.getString("day", null) != today) {
            p.edit().putString("day", today).putFloat("km", 0f).putLong("sec", 0).apply()
            s = DriveDay()
        }
        val speed = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
        val prev = last
        var km = s.km
        var sec = s.movingSec
        if (prev != null && loc.accuracy < 30f) {
            val dt = (loc.time - prev.time) / 1000
            if (speed > 4f && dt in 1..10) {
                km += prev.distanceTo(loc) / 1000.0
                sec += dt
            }
        }
        last = loc
        BlackBox.record(loc, speed.toInt())
        _state.value = DriveDay(speed.toInt().coerceAtLeast(0), km, sec, true)
        if (speed > 5f) _lastMoving.value = android.os.SystemClock.elapsedRealtime()
        app?.let { SpeedVolume.onSpeed(it, speed.toInt()) }
        val now = System.currentTimeMillis()
        if (now - lastSave > 30_000) {
            lastSave = now
            app?.getSharedPreferences("history", Context.MODE_PRIVATE)?.edit()
                ?.putString(today, "%.2f;%d".format(java.util.Locale.US, km, sec))?.apply()
            p?.edit()?.putFloat("km", km.toFloat())?.putLong("sec", sec)
                ?.putFloat("lat", lat.toFloat())?.putFloat("lon", lon.toFloat())?.apply()
        }
    }

    /** Last days (newest first): date, km, seconds of driving. */
    fun history(ctx: Context, days: Int = 14): List<Triple<LocalDate, Double, Long>> {
        val h = ctx.getSharedPreferences("history", Context.MODE_PRIVATE)
        return (0 until days).mapNotNull { i ->
            val d = LocalDate.now().minusDays(i.toLong())
            val v = h.getString(d.toString(), null) ?: return@mapNotNull null
            val (km, sec) = v.split(';').let { (it[0].toDoubleOrNull() ?: 0.0) to (it.getOrNull(1)?.toLongOrNull() ?: 0L) }
            if (km < 0.1) null else Triple(d, km, sec)
        }
    }

    fun speedAlert(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).getInt("speed_alert", 0)
    fun setSpeedAlert(ctx: Context, kmh: Int) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putInt("speed_alert", kmh).apply()

    /** True between sunrise and sunset at the current position (NOAA approximation). */
    fun isDaytime(now: Calendar = Calendar.getInstance()): Boolean {
        val (rise, set) = sunTimes(now) ?: return now.get(Calendar.HOUR_OF_DAY) in 7..18
        val h = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0
        return h in rise..set
    }

    /** Sunrise / sunset in local decimal hours. */
    private fun sunTimes(now: Calendar): Pair<Double, Double>? {
        val n = now.get(Calendar.DAY_OF_YEAR)
        val rad = PI / 180
        val gamma = 2 * PI / 365 * (n - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
            0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) - 0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) - 0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val x = cos(90.833 * rad) / (cos(lat * rad) * cos(decl)) - tan(lat * rad) * tan(decl)
        if (x < -1 || x > 1) return null
        val ha = acos(x) / rad
        val tz = TimeZone.getDefault().getOffset(now.timeInMillis) / 3_600_000.0
        val noon = (720 - 4 * lon - eqTime) / 60 + tz
        return (noon - ha * 4 / 60) to (noon + ha * 4 / 60)
    }

    @Suppress("unused") private fun unused() = floor(asin(0.0))
}
