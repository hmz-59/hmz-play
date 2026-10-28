package com.hmz.play.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.ByteArrayOutputStream
import java.util.UUID

enum class LinkStatus { NEED_PERMISSION, BLUETOOTH_OFF, WAITING, CONNECTING, CONNECTED }

data class PhoneNotif(
    val uid: Int,
    val appId: String,
    val appName: String,
    val title: String,
    val message: String,
    val category: Int,
    val time: Long,
)

data class Track(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Float = 0f,
    val playing: Boolean = false,
    val rate: Float = 1f,
    val elapsed: Float = 0f,
    val elapsedAt: Long = 0L,
    val playerName: String = "",
    val art: android.graphics.Bitmap? = null,
) {
    val isEmpty get() = title.isBlank() && artist.isBlank()

    fun positionAt(now: Long): Float {
        if (!playing) return elapsed
        val r = if (rate == 0f) 1f else rate
        val p = elapsed + (now - elapsedAt) / 1000f * r
        return if (duration > 0f) p.coerceIn(0f, duration) else p
    }
}

data class IncomingCall(val uid: Int, val caller: String)

object Uuids {
    val ANCS_SERVICE: UUID = UUID.fromString("7905F431-B5CE-4E99-A40F-4B1E122D00D0")
    val ANCS_NOTIFICATION_SOURCE: UUID = UUID.fromString("9FBF120D-6301-42D9-8C58-25E699A21DBD")
    val ANCS_CONTROL_POINT: UUID = UUID.fromString("69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9")
    val ANCS_DATA_SOURCE: UUID = UUID.fromString("22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB")
    val AMS_SERVICE: UUID = UUID.fromString("89D3502B-0F36-433A-8EF4-C502AD55F8DC")
    val AMS_REMOTE_COMMAND: UUID = UUID.fromString("9B3C81D8-57B1-4A8A-B8DF-0E56F7CA51C2")
    val AMS_ENTITY_UPDATE: UUID = UUID.fromString("2F7CABCE-808D-411F-9A0C-BB92BA96C102")
    val BATTERY_SERVICE: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    val BATTERY_LEVEL: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    val OWN_SERVICE: UUID = UUID.fromString("6E8B0001-4D2C-4F2A-9A7E-2B5C9D1E0A01")
    val OWN_CHAR: UUID = UUID.fromString("6E8B0002-4D2C-4F2A-9A7E-2B5C9D1E0A01")
}

/** AMS remote commands. */
object Cmd {
    const val PLAY = 0
    const val PAUSE = 1
    const val TOGGLE = 2
    const val NEXT = 3
    const val PREVIOUS = 4
    const val VOLUME_UP = 5
    const val VOLUME_DOWN = 6
}

/**
 * Talks to the iPhone over Bluetooth Low Energy using Apple's public services:
 *  - ANCS: notifications, incoming calls (answer / decline)
 *  - AMS: now playing + media remote control
 *  - Battery service
 *
 * The tablet advertises itself; the iPhone pairs from Settings > Bluetooth, then the tablet
 * opens a GATT client connection back to the iPhone and subscribes to those services.
 */
@SuppressLint("MissingPermission")
object IphoneLink {
    private const val TAG = "IphoneLink"
    private const val PREFS = "link"
    private const val KEY_ADDR = "iphone_addr"

    private val _status = MutableStateFlow(LinkStatus.NEED_PERMISSION)
    val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private val _tabletName = MutableStateFlow("cette tablette")
    val tabletName: StateFlow<String> = _tabletName.asStateFlow()

    private val _phoneName = MutableStateFlow<String?>(null)
    val phoneName: StateFlow<String?> = _phoneName.asStateFlow()

    private val _battery = MutableStateFlow<Int?>(null)
    val battery: StateFlow<Int?> = _battery.asStateFlow()

    private val _notifications = MutableStateFlow<List<PhoneNotif>>(emptyList())
    val notifications: StateFlow<List<PhoneNotif>> = _notifications.asStateFlow()

    private val _track = MutableStateFlow(Track())

    /** iPhone music: Apple Media Service when linked, otherwise the car's own media session (Bluetooth music). */
    val track: StateFlow<Track> = combine(_track, com.hmz.play.data.MediaBridge.info) { ams, m ->
        if (!ams.isEmpty || m.isEmpty) ams else Track(
            title = m.title, artist = m.artist, album = m.album, duration = m.durationSec,
            playing = m.playing, rate = m.speed, elapsed = m.positionSec, elapsedAt = m.positionAt,
            playerName = m.source, art = m.art,
        )
    }.stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.Eagerly, Track())

    private val _call = MutableStateFlow<IncomingCall?>(null)
    val call: StateFlow<IncomingCall?> = _call.asStateFlow()

    private val _banners = MutableSharedFlow<PhoneNotif>(extraBufferCapacity = 8)
    val banners: SharedFlow<PhoneNotif> = _banners.asSharedFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    fun note(msg: String) {
        Log.i(TAG, msg)
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.FRANCE).format(java.util.Date())
        _log.update { (listOf("$t  $msg") + it).take(40) }
    }

    /** Devices already paired with this head unit (classic Bluetooth), to try a direct link. */
    fun bondedDevices(): List<Pair<String, String>> = runCatching {
        adapter?.bondedDevices.orEmpty().map { (it.name ?: it.address) to it.address }
    }.getOrDefault(emptyList())

    fun connectTo(address: String) = h.post {
        val d = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return@post
        note("Connexion directe à ${d.name ?: address}…")
        connect(d, auto = false)
    }

    private val thread = HandlerThread("hmz-ble").apply { start() }
    private val h = Handler(thread.looper)

    private var app: Context? = null
    private var adapter: BluetoothAdapter? = null
    private var server: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertising = false
    private var gatt: BluetoothGatt? = null
    private var receiversRegistered = false
    private var discoverAttempts = 0

    // ---------------------------------------------------------------- public API

    fun start(ctx: Context) {
        val c = ctx.applicationContext
        h.post { startInternal(c) }
    }

    fun command(cmd: Int) = h.post {
        val c = gatt?.getService(Uuids.AMS_SERVICE)?.getCharacteristic(Uuids.AMS_REMOTE_COMMAND)
        if (c == null || _track.value.isEmpty) {
            if (com.hmz.play.data.MediaBridge.command(cmd)) return@post
        }
        if (c == null) return@post
        if (cmd == Cmd.TOGGLE || cmd == Cmd.PLAY || cmd == Cmd.PAUSE) {
            // Optimistic UI update so the button feels instant.
            _track.update {
                val now = SystemClock.elapsedRealtime()
                val playing = when (cmd) {
                    Cmd.PLAY -> true
                    Cmd.PAUSE -> false
                    else -> !it.playing
                }
                it.copy(playing = playing, elapsed = it.positionAt(now), elapsedAt = now)
            }
        }
        enqueue(Op.Write(c, byteArrayOf(cmd.toByte()), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))
    }

    fun answerCall(accept: Boolean) = h.post {
        val call = _call.value ?: return@post
        val cp = gatt?.getService(Uuids.ANCS_SERVICE)?.getCharacteristic(Uuids.ANCS_CONTROL_POINT)
        if (cp != null) {
            val data = byteArrayOf(2) + le32(call.uid) + byteArrayOf(if (accept) 0 else 1)
            enqueue(Op.Write(cp, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))
        }
        _call.value = null
    }

    fun dismissCall() { _call.value = null }

    fun clearNotifications() { _notifications.value = emptyList() }

    private val _pairingUntil = MutableStateFlow(0L)
    /** Elapsed-realtime deadline of the pairing mode, 0 when off. */
    val pairingUntil: StateFlow<Long> = _pairingUntil.asStateFlow()
    private var savedName: String? = null
    private const val PAIR_NAME = "HMZ Play"

    /**
     * Pairing mode: for 2 minutes the head unit shows up as "HMZ Play" instead of the car's
     * Bluetooth name, so the iPhone lists it as a NEW device (Settings > Bluetooth > Other devices)
     * and pairs with HMZ Play itself. The original name comes back afterwards.
     */
    fun startPairingMode() = h.post {
        val a = adapter ?: return@post
        if (savedName == null) savedName = runCatching { a.name }.getOrNull()
        val ok = runCatching { a.setName(PAIR_NAME) }.getOrDefault(false)
        note(if (ok) "Mode jumelage : visible sous « $PAIR_NAME » pendant 2 min" else "Impossible de renommer le Bluetooth de l'autoradio")
        if (!ok) return@post
        _tabletName.value = PAIR_NAME
        restartAdvertising()
        _pairingUntil.value = SystemClock.elapsedRealtime() + 120_000
        h.removeCallbacks(endPairing)
        h.postDelayed(endPairing, 120_000)
    }

    fun stopPairingMode() = h.post { endPairing.run() }

    private val endPairing = Runnable {
        val a = adapter
        val n = savedName
        if (a != null && n != null) {
            runCatching { a.setName(n) }
            _tabletName.value = n
            note("Fin du mode jumelage, nom rétabli : $n")
        }
        savedName = null
        _pairingUntil.value = 0L
        restartAdvertising()
    }

    private fun restartAdvertising() {
        stopAdvertisingAny()
        h.postDelayed({ startAdvertising() }, 400)
    }

    fun forgetPhone() = h.post {
        prefs()?.edit()?.remove(KEY_ADDR)?.apply()
        gatt?.let { runCatching { it.disconnect(); it.close() } }
        gatt = null
        resetSession()
        if (adapter?.isEnabled == true) _status.value = LinkStatus.WAITING
    }

    fun hasPermissions(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE).all {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // ---------------------------------------------------------------- lifecycle

    private fun prefs() = app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun startInternal(ctx: Context) {
        app = ctx
        val mgr = ctx.getSystemService(BluetoothManager::class.java)
        adapter = mgr?.adapter
        if (adapter == null) {
            note("Aucun adaptateur Bluetooth accessible à Android")
            _status.value = LinkStatus.BLUETOOTH_OFF
            return
        }
        if (!hasPermissions(ctx)) {
            _status.value = LinkStatus.NEED_PERMISSION
            return
        }
        registerReceivers(ctx)
        if (adapter?.isEnabled == true) bringUp() else _status.value = LinkStatus.BLUETOOTH_OFF
    }

    private fun registerReceivers(ctx: Context) {
        if (receiversRegistered) return
        receiversRegistered = true
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(ctx, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                            BluetoothAdapter.STATE_ON -> bringUp()
                            BluetoothAdapter.STATE_OFF -> {
                                tearDown()
                                _status.value = LinkStatus.BLUETOOTH_OFF
                            }
                        }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                        val g = gatt ?: return
                        val dev = deviceFrom(intent) ?: return
                        if (dev.address == g.device.address) note("Jumelage : état $state")
                        if (dev.address == g.device.address && state == BluetoothDevice.BOND_BONDED) {
                            note("Jumelage terminé, recherche des services")
                            h.postDelayed({ gatt?.discoverServices() }, 600)
                        }
                    }
                }
            }
        }, filter, null, h, ContextCompat.RECEIVER_EXPORTED)
    }

    @Suppress("DEPRECATION")
    private fun deviceFrom(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    private fun bringUp() {
        val a = adapter ?: return
        _tabletName.value = runCatching { a.name }.getOrNull() ?: "cette tablette"
        note("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        note("Bluetooth : ${_tabletName.value} · BLE ${if (runCatching { a.isMultipleAdvertisementSupported }.getOrDefault(false)) "OK" else "annonce non garantie"} · périph. ${if (a.bluetoothLeAdvertiser != null) "OK" else "NON SUPPORTÉ"}")
        openServer()
        startAdvertising()
        val saved = prefs()?.getString(KEY_ADDR, null)
        if (saved != null && gatt == null) {
            runCatching { a.getRemoteDevice(saved) }.getOrNull()?.let { connect(it, auto = true) }
        }
        if (_status.value != LinkStatus.CONNECTED && _status.value != LinkStatus.CONNECTING) {
            _status.value = LinkStatus.WAITING
        }
    }

    private fun tearDown() {
        stopAdvertisingAny()
        advertising = false
        gatt?.let { runCatching { it.close() } }
        gatt = null
        server?.let { runCatching { it.close() } }
        server = null
        resetSession()
    }

    private fun resetSession() {
        ops.clear()
        inFlight = null
        h.removeCallbacks(opTimeout)
        pending.clear()
        fetching = null
        dataBuf.reset()
        _call.value = null
        _battery.value = null
        _track.value = Track()
    }

    // ---------------------------------------------------------------- peripheral side

    private fun openServer() {
        if (server != null) return
        val mgr = app?.getSystemService(BluetoothManager::class.java) ?: return
        server = runCatching { mgr.openGattServer(app, serverCallback) }.getOrNull()
        val svc = BluetoothGattService(Uuids.OWN_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        svc.addCharacteristic(
            BluetoothGattCharacteristic(
                Uuids.OWN_CHAR,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            ),
        )
        runCatching { server?.addService(svc) }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            h.post {
                if (newState == BluetoothProfile.STATE_CONNECTED && gatt == null) {
                    note("${device.name ?: device.address} s'est connecté, ouverture du lien")
                    connect(device, auto = false)
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic,
        ) {
            runCatching {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, "HMZ".toByteArray())
            }
        }
    }

    private var advSet = false

    /**
     * Advertises as a separate BLE accessory that asks for Apple's notification service, so the
     * iPhone lists it on its own instead of merging it with the car's audio Bluetooth.
     * Uses an advertising set (own random address where the chip allows it), falls back to legacy.
     */
    private fun startAdvertising(withName: Boolean = true) {
        if (advertising) return
        val adv = adapter?.bluetoothLeAdvertiser ?: run { note("Cet autoradio ne peut pas s'annoncer en BLE"); return }
        advertiser = adv
        val data = AdvertiseData.Builder().setIncludeDeviceName(false).apply {
            if (Build.VERSION.SDK_INT >= 31) {
                addServiceSolicitationUuid(ParcelUuid(Uuids.ANCS_SERVICE))
                addServiceSolicitationUuid(ParcelUuid(Uuids.AMS_SERVICE))
            } else {
                addServiceUuid(ParcelUuid(Uuids.OWN_SERVICE))
            }
        }.build()
        val scan = AdvertiseData.Builder().setIncludeDeviceName(withName).build()
        advNameRetry = withName

        if (Build.VERSION.SDK_INT >= 26 && !advSetFailed) {
            val pb = android.bluetooth.le.AdvertisingSetParameters.Builder()
                .setLegacyMode(true)
                .setConnectable(true)
                .setScannable(true)
                .setInterval(android.bluetooth.le.AdvertisingSetParameters.INTERVAL_LOW)
                .setTxPowerLevel(android.bluetooth.le.AdvertisingSetParameters.TX_POWER_MEDIUM)
            if (Build.VERSION.SDK_INT >= 34) {
                // setOwnAddressType(ADDRESS_TYPE_RANDOM = 1), called by reflection (newer SDK API)
                runCatching { pb.javaClass.getMethod("setOwnAddressType", Int::class.javaPrimitiveType).invoke(pb, 1) }
            }
            val ok = runCatching { adv.startAdvertisingSet(pb.build(), data, scan, null, null, setCallback) }.isSuccess
            if (ok) return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        runCatching { adv.startAdvertising(settings, data, scan, advCallback) }
            .onFailure { note("Annonce BLE impossible : ${it.message}") }
    }

    private var advSetFailed = false

    private val setCallback = object : android.bluetooth.le.AdvertisingSetCallback() {
        override fun onAdvertisingSetStarted(set: android.bluetooth.le.AdvertisingSet?, txPower: Int, status: Int) {
            h.post {
                if (status == ADVERTISE_SUCCESS) {
                    advertising = true
                    advSet = true
                    note("Annonce BLE (mode accessoire) active : cherche « ${_tabletName.value} » sur l'iPhone")
                } else {
                    note("Mode accessoire refusé (code $status), mode simple")
                    advSetFailed = true
                    advertising = false
                    startAdvertising(advNameRetry)
                }
            }
        }

        override fun onAdvertisingSetStopped(set: android.bluetooth.le.AdvertisingSet?) {
            h.post { advertising = false; advSet = false }
        }
    }

    private fun stopAdvertisingAny() {
        runCatching {
            if (advSet) advertiser?.stopAdvertisingSet(setCallback) else if (advertising) advertiser?.stopAdvertising(advCallback)
        }
        advertising = false
        advSet = false
    }

    private var advNameRetry = true

    private val advCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            note("Annonce BLE active : visible par l'iPhone")
            advertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            note("Échec annonce BLE (code $errorCode)")
            advertising = false
            if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE && advNameRetry) {
                h.post { startAdvertising(withName = false) }
            }
        }
    }

    // ---------------------------------------------------------------- central (client) side

    private fun connect(device: BluetoothDevice, auto: Boolean) {
        val ctx = app ?: return
        gatt?.let { runCatching { it.close() } }
        resetSession()
        discoverAttempts = 0
        _status.value = if (auto) LinkStatus.WAITING else LinkStatus.CONNECTING
        gatt = device.connectGatt(ctx, auto, clientCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val clientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            h.post {
                if (g != gatt) return@post
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    note("Lien établi avec ${g.device.name ?: g.device.address} (état jumelage ${g.device.bondState})")
                    _status.value = LinkStatus.CONNECTING
                    _phoneName.value = runCatching { g.device.name }.getOrNull()
                    ops.clear(); inFlight = null
                    if (!g.requestMtu(247)) g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    note("Déconnecté (code $status)")
                    val dev = g.device
                    runCatching { g.close() }
                    gatt = null
                    resetSession()
                    _status.value = if (adapter?.isEnabled == true) LinkStatus.WAITING else LinkStatus.BLUETOOTH_OFF
                    val known = prefs()?.getString(KEY_ADDR, null) == dev.address ||
                        dev.bondState == BluetoothDevice.BOND_BONDED
                    if (known) h.postDelayed({ if (gatt == null) connect(dev, auto = true) }, 1500)
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            h.post { if (g == gatt) g.discoverServices() }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            h.post { if (g == gatt) setup(g) }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            h.post {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "descriptor write failed $status")
                    if (g.device.bondState == BluetoothDevice.BOND_NONE) g.device.createBond()
                }
                opDone()
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            h.post { opDone() }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            val v = value.copyOf()
            h.post { handleValue(c.uuid, v); opDone() }
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= 33) return
            val v = c.value?.copyOf() ?: ByteArray(0)
            h.post { handleValue(c.uuid, v); opDone() }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            val v = value.copyOf()
            h.post { handleValue(c.uuid, v) }
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= 33) return
            val v = c.value?.copyOf() ?: return
            h.post { handleValue(c.uuid, v) }
        }
    }

    private fun setup(g: BluetoothGatt) {
        val ancs = g.getService(Uuids.ANCS_SERVICE)
        val ams = g.getService(Uuids.AMS_SERVICE)
        note("Services : notifications ${if (ancs != null) "OK" else "—"} · musique ${if (ams != null) "OK" else "—"}")
        if (ancs == null && ams == null) {
            // Apple hides these services until the link is bonded/encrypted.
            if (g.device.bondState == BluetoothDevice.BOND_NONE) {
                note("Services Apple cachés : demande de jumelage")
                g.device.createBond()
            } else if (discoverAttempts++ < 6) {
                h.postDelayed({ if (g == gatt) g.discoverServices() }, 2000)
            }
            return
        }
        ops.clear(); inFlight = null
        ancs?.let { s ->
            s.getCharacteristic(Uuids.ANCS_DATA_SOURCE)?.let { enqueue(Op.Notify(it)) }
            s.getCharacteristic(Uuids.ANCS_NOTIFICATION_SOURCE)?.let { enqueue(Op.Notify(it)) }
        }
        ams?.getCharacteristic(Uuids.AMS_ENTITY_UPDATE)?.let { eu ->
            enqueue(Op.Notify(eu))
            val w = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            enqueue(Op.Write(eu, byteArrayOf(0, 0, 1), w))        // Player: name, playback info
            enqueue(Op.Write(eu, byteArrayOf(2, 0, 1, 2, 3), w))  // Track: artist, album, title, duration
        }
        g.getService(Uuids.BATTERY_SERVICE)?.getCharacteristic(Uuids.BATTERY_LEVEL)?.let {
            enqueue(Op.Notify(it))
            enqueue(Op.Read(it))
        }
        prefs()?.edit()?.putString(KEY_ADDR, g.device.address)?.apply()
        _phoneName.value = runCatching { g.device.name }.getOrNull() ?: _phoneName.value
        _status.value = LinkStatus.CONNECTED
    }

    // ---------------------------------------------------------------- GATT operation queue

    private sealed class Op {
        class Notify(val c: BluetoothGattCharacteristic) : Op()
        class Write(val c: BluetoothGattCharacteristic, val data: ByteArray, val type: Int) : Op()
        class Read(val c: BluetoothGattCharacteristic) : Op()
    }

    private val ops = ArrayDeque<Op>()
    private var inFlight: Op? = null
    private val opTimeout = Runnable {
        Log.w(TAG, "GATT op timeout")
        inFlight = null
        nextOp()
    }

    private fun enqueue(op: Op) {
        ops.addLast(op)
        if (inFlight == null) nextOp()
    }

    private fun opDone() {
        h.removeCallbacks(opTimeout)
        inFlight = null
        nextOp()
    }

    private fun nextOp() {
        val g = gatt
        if (g == null) { ops.clear(); inFlight = null; return }
        while (true) {
            val op = ops.removeFirstOrNull() ?: run { inFlight = null; return }
            val ok = runCatching {
                when (op) {
                    is Op.Notify -> enableNotify(g, op.c)
                    is Op.Write -> write(g, op.c, op.data, op.type)
                    is Op.Read -> g.readCharacteristic(op.c)
                }
            }.getOrDefault(false)
            if (ok) {
                inFlight = op
                h.postDelayed(opTimeout, 4000)
                return
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun enableNotify(g: BluetoothGatt, c: BluetoothGattCharacteristic): Boolean {
        g.setCharacteristicNotification(c, true)
        val d = c.getDescriptor(Uuids.CCCD) ?: return false
        val v = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(d, v) == BluetoothStatusCodes.SUCCESS
        } else {
            d.value = v
            g.writeDescriptor(d)
        }
    }

    @Suppress("DEPRECATION")
    private fun write(g: BluetoothGatt, c: BluetoothGattCharacteristic, data: ByteArray, type: Int): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, data, type) == BluetoothStatusCodes.SUCCESS
        } else {
            c.writeType = type
            c.value = data
            g.writeCharacteristic(c)
        }
    }

    // ---------------------------------------------------------------- incoming data

    private fun handleValue(uuid: UUID, v: ByteArray) {
        when (uuid) {
            Uuids.ANCS_NOTIFICATION_SOURCE -> onNotificationSource(v)
            Uuids.ANCS_DATA_SOURCE -> onDataSource(v)
            Uuids.AMS_ENTITY_UPDATE -> onEntityUpdate(v)
            Uuids.BATTERY_LEVEL -> if (v.isNotEmpty()) _battery.value = v[0].toInt() and 0xFF
        }
    }

    // ANCS -------------------------------------------------------------

    private class Pending(val uid: Int, val category: Int)

    private val pending = ArrayDeque<Pending>()
    private var fetching: Pending? = null
    private val dataBuf = ByteArrayOutputStream()
    private val fetchTimeout = Runnable {
        fetching = null
        dataBuf.reset()
        fetchNext()
    }

    private fun onNotificationSource(v: ByteArray) {
        if (v.size < 8) return
        val event = v[0].toInt()
        val flags = v[1].toInt() and 0xFF
        val category = v[2].toInt() and 0xFF
        val uid = readLe32(v, 4)
        when (event) {
            0 -> { // added
                val preExisting = flags and 0x04 != 0
                if (!preExisting || category == 1) {
                    pending.addLast(Pending(uid, category))
                    if (fetching == null) fetchNext()
                }
            }
            2 -> { // removed
                _notifications.update { list -> list.filterNot { it.uid == uid } }
                if (_call.value?.uid == uid) _call.value = null
            }
        }
    }

    private fun fetchNext() {
        val p = pending.removeFirstOrNull() ?: run { fetching = null; return }
        val cp = gatt?.getService(Uuids.ANCS_SERVICE)?.getCharacteristic(Uuids.ANCS_CONTROL_POINT)
            ?: run { fetching = null; return }
        fetching = p
        dataBuf.reset()
        // GetNotificationAttributes: AppIdentifier, Title (max 64), Message (max 240)
        val cmd = byteArrayOf(0) + le32(p.uid) +
            byteArrayOf(0) +
            byteArrayOf(1, 64, 0) +
            byteArrayOf(3, 240.toByte(), 0)
        enqueue(Op.Write(cp, cmd, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT))
        h.removeCallbacks(fetchTimeout)
        h.postDelayed(fetchTimeout, 3000)
    }

    private fun onDataSource(v: ByteArray) {
        if (fetching == null) return
        dataBuf.write(v)
        val b = dataBuf.toByteArray()
        if (b.size < 5) return
        if (b[0].toInt() != 0) { dataBuf.reset(); return }
        val uid = readLe32(b, 1)
        var i = 5
        val attrs = HashMap<Int, String>()
        repeat(3) {
            if (i + 3 > b.size) return
            val id = b[i].toInt() and 0xFF
            val len = (b[i + 1].toInt() and 0xFF) or ((b[i + 2].toInt() and 0xFF) shl 8)
            i += 3
            if (i + len > b.size) return
            attrs[id] = String(b, i, len, Charsets.UTF_8)
            i += len
        }
        h.removeCallbacks(fetchTimeout)
        val p = fetching
        fetching = null
        dataBuf.reset()
        if (p != null) publish(uid, p.category, attrs)
        fetchNext()
    }

    private fun publish(uid: Int, category: Int, attrs: Map<Int, String>) {
        val appId = attrs[0].orEmpty()
        val title = attrs[1].orEmpty()
        val message = attrs[3].orEmpty()
        if (category == 1) {
            _call.value = IncomingCall(uid, title.ifBlank { "Appel entrant" })
            return
        }
        if (title.isBlank() && message.isBlank()) return
        val n = PhoneNotif(uid, appId, appName(appId), title, message, category, System.currentTimeMillis())
        _notifications.update { list -> (listOf(n) + list.filterNot { it.uid == uid }).take(60) }
        _banners.tryEmit(n)
    }

    fun appName(appId: String): String = when (appId) {
        "com.apple.MobileSMS" -> "Messages"
        "com.apple.mobilephone" -> "Téléphone"
        "com.apple.mobilemail" -> "Mail"
        "com.apple.mobilecal" -> "Calendrier"
        "com.apple.reminders" -> "Rappels"
        "net.whatsapp.WhatsApp" -> "WhatsApp"
        "com.facebook.Messenger" -> "Messenger"
        "com.burbn.instagram" -> "Instagram"
        "com.toyopagroup.picaboo" -> "Snapchat"
        "ph.telegra.Telegraph" -> "Telegram"
        "com.google.Gmail" -> "Gmail"
        "com.ubercab.UberClient", "com.ubercab.driver" -> "Uber"
        "com.heetch.heetch", "com.heetch.driver" -> "Heetch"
        "ee.mtakso.client", "ee.mtakso.driver" -> "Bolt"
        "com.waze.iphone" -> "Waze"
        "com.spotify.client" -> "Spotify"
        "" -> "iPhone"
        else -> appId.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    // AMS -------------------------------------------------------------

    private fun onEntityUpdate(v: ByteArray) {
        if (v.size < 3) return
        val entity = v[0].toInt()
        val attr = v[1].toInt()
        val s = String(v, 3, v.size - 3, Charsets.UTF_8)
        when (entity) {
            0 -> when (attr) { // player
                0 -> _track.update { it.copy(playerName = s) }
                1 -> {
                    val parts = s.split(',')
                    val state = parts.getOrNull(0)?.toIntOrNull() ?: 0
                    val rate = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
                    val elapsed = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
                    _track.update {
                        it.copy(playing = state == 1, rate = rate, elapsed = elapsed, elapsedAt = SystemClock.elapsedRealtime())
                    }
                }
            }
            2 -> when (attr) { // track
                0 -> _track.update { it.copy(artist = s) }
                1 -> _track.update { it.copy(album = s) }
                2 -> _track.update { it.copy(title = s) }
                3 -> _track.update { it.copy(duration = s.toFloatOrNull() ?: 0f) }
            }
        }
    }

    // ---------------------------------------------------------------- utils

    private fun le32(x: Int) = byteArrayOf(
        (x and 0xFF).toByte(), ((x shr 8) and 0xFF).toByte(), ((x shr 16) and 0xFF).toByte(), ((x shr 24) and 0xFF).toByte(),
    )

    private fun readLe32(b: ByteArray, i: Int) =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) or
            ((b[i + 2].toInt() and 0xFF) shl 16) or ((b[i + 3].toInt() and 0xFF) shl 24)
}
