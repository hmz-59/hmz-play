package com.hmz.play.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.hmz.play.ble.IphoneLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DoorState(
    val frontLeft: Boolean = false,
    val frontRight: Boolean = false,
    val rearLeft: Boolean = false,
    val rearRight: Boolean = false,
    val trunk: Boolean = false,
    val hood: Boolean = false,
) {
    val any get() = frontLeft || frontRight || rearLeft || rearRight || trunk || hood
    val labels get() = listOfNotNull(
        "avant gauche".takeIf { frontLeft }, "avant droite".takeIf { frontRight },
        "arrière gauche".takeIf { rearLeft }, "arrière droite".takeIf { rearRight },
        "coffre".takeIf { trunk }, "capot".takeIf { hood },
    )
}

/**
 * Door status from the head unit's CAN box (the "CarCan" app reads the Prius CAN bus).
 * There is no standard Android API for this: each head-unit maker broadcasts it differently.
 * We listen to the broadcasts these units commonly use and read any door-looking extra.
 * Every broadcast received is written to the Diagnostic, to adapt to this unit if needed.
 */
object Doors {
    private val _state = MutableStateFlow(DoorState())
    val state: StateFlow<DoorState> = _state.asStateFlow()

    private val _source = MutableStateFlow<String?>(null)
    /** Last broadcast that carried door data (null = nothing received yet). */
    val source: StateFlow<String?> = _source.asStateFlow()

    private val ACTIONS = listOf(
        "com.syu.canbus.door", "com.syu.ms.door", "com.syu.canbus.action.DOOR",
        "com.microntek.canbus.door", "com.microntek.CAN_DOOR",
        "android.intent.action.CAN_DOOR", "android.intent.action.CAR_DOOR", "com.android.canbus.door",
        "com.ts.can.door", "com.ts.canbus.DOOR_STATE", "com.carcan.door", "com.carcan.DOOR_CHANGED",
        "com.hzbhd.canbus.door", "com.fyt.canbus.door", "com.tw.canbus.door", "com.nwd.can.door",
        "com.zhonghong.canbus.door", "com.ys.canbus.door", "com.car.canbus.DOOR",
    )
    private var started = false
    private val main = Handler(Looper.getMainLooper())

    fun start(ctx: Context) {
        if (started) return
        started = true
        val filter = IntentFilter().apply { ACTIONS.forEach { addAction(it) } }
        ContextCompat.registerReceiver(ctx.applicationContext, object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = onBroadcast(i)
        }, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun onBroadcast(i: Intent) {
        val ex = i.extras ?: return
        val dump = ex.keySet().joinToString { "$it=${ex.get(it)}" }
        IphoneLink.note("Portes ← ${i.action} : $dump")
        var s = DoorState()
        var found = false
        for (k in ex.keySet()) {
            val key = k.lowercase().replace("_", "").replace("-", "")
            // whole words only ("door_fl", "frontLeftDoor"…), so "flag" or "error" never count as a door
            val w = k.split(Regex("[^A-Za-z0-9]+|(?<=[a-z0-9])(?=[A-Z])")).filter { it.isNotBlank() }.joinToString(" ").lowercase()
            val v = ex.get(k)
            val open = when (v) {
                is Boolean -> v
                is Int -> v != 0
                is String -> v == "1" || v.equals("true", true) || v.equals("open", true)
                else -> continue
            }
            when {
                ("door" in key || "doors" in key) && v is Int && ("state" in key || "mask" in key || key == "door" || key == "doors") -> {
                    // Common bitmask: bit0 FL, bit1 FR, bit2 RL, bit3 RR, bit4 trunk, bit5 hood
                    s = DoorState(v and 1 != 0, v and 2 != 0, v and 4 != 0, v and 8 != 0, v and 16 != 0, v and 32 != 0)
                    found = true
                }
                Regex("\\b(fl|lf|driver|front ?left|left ?front)\\b").containsMatchIn(w) -> { s = s.copy(frontLeft = open); found = true }
                Regex("\\b(fr|rf|passenger|front ?right|right ?front)\\b").containsMatchIn(w) -> { s = s.copy(frontRight = open); found = true }
                Regex("\\b(rl|lr|rear ?left|left ?rear|back ?left)\\b").containsMatchIn(w) -> { s = s.copy(rearLeft = open); found = true }
                Regex("\\b(rr|rear ?right|right ?rear|back ?right)\\b").containsMatchIn(w) -> { s = s.copy(rearRight = open); found = true }
                Regex("\\b(trunk|boot|tailgate|hatch|back ?door)\\b").containsMatchIn(w) -> { s = s.copy(trunk = open); found = true }
                Regex("\\b(hood|bonnet)\\b").containsMatchIn(w) -> { s = s.copy(hood = open); found = true }
            }
        }
        if (found) {
            _source.value = i.action
            _state.value = s
        }
    }

    /** Settings > Portes > Test: opens each door in turn on screen, then closes everything. */
    fun demo() {
        val steps = listOf(
            DoorState(frontLeft = true), DoorState(frontRight = true), DoorState(rearLeft = true),
            DoorState(rearRight = true), DoorState(trunk = true), DoorState(),
        )
        steps.forEachIndexed { i, st -> main.postDelayed({ _state.value = st }, i * 2500L) }
    }
}
