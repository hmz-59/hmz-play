package com.hmz.play.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the head unit's GPS receiver really sees (to tell "no antenna" from "no sky"). */
data class GpsHealth(
    val enabled: Boolean = false,      // GPS turned on in Android
    val mode: String = "",             // location mode
    val seen: Int = 0,                 // satellites heard
    val used: Int = 0,                 // satellites used for the position
    val bestSignal: Float = 0f,        // best C/N0 (dB-Hz): ~0 = no antenna, >25 = good
    val statusEvents: Boolean = false, // the receiver talks at all
)

@SuppressLint("MissingPermission")
object GpsInfo {
    private val _h = MutableStateFlow(GpsHealth())
    val health: StateFlow<GpsHealth> = _h.asStateFlow()
    private var started = false

    fun start(ctx: Context) {
        if (started) return
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return
        started = true
        refreshSettings(ctx)
        runCatching {
            lm.registerGnssStatusCallback(object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(s: GnssStatus) {
                    var used = 0
                    var best = 0f
                    for (i in 0 until s.satelliteCount) {
                        if (s.usedInFix(i)) used++
                        if (s.getCn0DbHz(i) > best) best = s.getCn0DbHz(i)
                    }
                    _h.value = _h.value.copy(seen = s.satelliteCount, used = used, bestSignal = best, statusEvents = true)
                }
            }, Handler(Looper.getMainLooper()))
        }
    }

    fun refreshSettings(ctx: Context) {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return
        val on = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        @Suppress("DEPRECATION")
        val mode = when (runCatching { Settings.Secure.getInt(ctx.contentResolver, Settings.Secure.LOCATION_MODE) }.getOrDefault(-1)) {
            0 -> "désactivée"
            1 -> "appareil seul (GPS)"
            2 -> "économie de batterie (sans GPS !)"
            3 -> "haute précision"
            else -> "?"
        }
        _h.value = _h.value.copy(enabled = on, mode = mode)
    }
}
