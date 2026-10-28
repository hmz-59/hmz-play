package com.hmz.play.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Parcelable
import androidx.core.content.ContextCompat

/**
 * Calls and text messages through the car's CLASSIC Bluetooth link with the iPhone — no app on
 * the iPhone, no BLE pairing:
 *  - HFP client  (profile 16): incoming call number/name, accept / decline
 *  - MAP client  (profile 18): incoming SMS / iMessage (iPhone: Réglages > Bluetooth > Car BT prius (i)
 *    > « Afficher les notifications »)
 * These profiles exist on many car head units; the diagnostic shows whether this one has them.
 * The Android classes are hidden APIs, so they're reached through broadcasts and reflection.
 */
@SuppressLint("MissingPermission")
object CarBluetooth {
    private const val HEADSET_CLIENT = 16
    private const val MAP_CLIENT = 18

    // Android 9 action / extra names (hidden constants)
    private const val ACTION_CALL_CHANGED = "android.bluetooth.headsetclient.profile.action.AG_CALL_CHANGED"
    private const val EXTRA_CALL = "android.bluetooth.headsetclient.extra.CALL"
    private const val ACTION_MAP_MESSAGE = "android.bluetooth.mapmce.profile.action.MESSAGE_RECEIVED"
    private const val EXTRA_SENDER_NAME = "android.bluetooth.mapmce.profile.extra.SENDER_CONTACT_NAME"
    private const val EXTRA_SENDER_URI = "android.bluetooth.mapmce.profile.extra.SENDER_CONTACT_URI"
    private const val EXTRA_HANDLE = "android.bluetooth.mapmce.profile.extra.MESSAGE_HANDLE"

    // BluetoothHeadsetClientCall states
    private const val CALL_STATE_INCOMING = 4
    private const val CALL_STATE_WAITING = 5
    private const val CALL_STATE_TERMINATED = 7

    private var started = false
    private var hfp: BluetoothProfile? = null
    private var currentDevice: BluetoothDevice? = null

    fun start(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return

        probe(app, adapter, HEADSET_CLIENT, "Appels (HFP)") { hfp = it }
        probe(app, adapter, MAP_CLIENT, "Messages (MAP)") { }

        val filter = IntentFilter().apply {
            addAction(ACTION_CALL_CHANGED)
            addAction(ACTION_MAP_MESSAGE)
        }
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_CALL_CHANGED -> onCall(intent)
                    ACTION_MAP_MESSAGE -> onMessage(intent)
                }
            }
        }, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun probe(ctx: Context, a: BluetoothAdapter, profile: Int, label: String, keep: (BluetoothProfile) -> Unit) {
        val ok = runCatching {
            a.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                    keep(proxy)
                    val n = runCatching { proxy.connectedDevices.size }.getOrDefault(0)
                    IphoneLink.note("$label : disponible${if (n > 0) " · iPhone connecté" else " · iPhone non connecté"}")
                }

                override fun onServiceDisconnected(p: Int) {
                    IphoneLink.note("$label : déconnecté")
                }
            }, profile)
        }.getOrDefault(false)
        if (!ok) IphoneLink.note("$label : absent sur cet autoradio")
    }

    private fun onCall(intent: Intent) {
        val call = intent.getParcelableExtra<Parcelable>(EXTRA_CALL) ?: return
        currentDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val state = runCatching { call.javaClass.getMethod("getState").invoke(call) as Int }.getOrDefault(-1)
        val number = runCatching { call.javaClass.getMethod("getNumber").invoke(call) as String? }.getOrNull().orEmpty()
        when (state) {
            CALL_STATE_INCOMING, CALL_STATE_WAITING ->
                IphoneLink.setExternalCall(IncomingCall(-1, number.ifBlank { "Appel entrant" }))
            else -> if (state == CALL_STATE_TERMINATED || state >= 0) IphoneLink.setExternalCall(null)
        }
    }

    private fun onMessage(intent: Intent) {
        val name = intent.getStringExtra(EXTRA_SENDER_NAME)
            ?: intent.getStringExtra(EXTRA_SENDER_URI)?.substringAfter("tel:")
            ?: "Message"
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val handle = intent.getStringExtra(EXTRA_HANDLE) ?: "${System.currentTimeMillis()}"
        IphoneLink.pushExternalNotif(
            PhoneNotif(
                uid = -(handle.hashCode() and 0x7fffffff) - 1,
                appId = "com.apple.MobileSMS",
                appName = "Messages",
                title = name,
                message = text,
                category = 4,
                time = System.currentTimeMillis(),
            ),
        )
    }

    /** Accept / decline through the Headset Client profile (hidden API, by reflection). */
    fun answer(accept: Boolean) {
        val p = hfp ?: return
        val dev = currentDevice ?: runCatching { p.connectedDevices.firstOrNull() }.getOrNull() ?: return
        runCatching {
            if (accept) {
                p.javaClass.getMethod("acceptCall", BluetoothDevice::class.java, Int::class.javaPrimitiveType).invoke(p, dev, 0)
            } else {
                p.javaClass.getMethod("rejectCall", BluetoothDevice::class.java).invoke(p, dev)
            }
        }.onFailure { IphoneLink.note("Commande d'appel refusée : ${it.cause?.message ?: it.message}") }
    }
}
