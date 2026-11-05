package com.hmz.play.data

import android.content.Context
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

private fun get(url: String): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 8000
    c.readTimeout = 12000
    c.setRequestProperty("User-Agent", "HMZPlay/1.0 (car head unit)")
    return c.inputStream.bufferedReader().use { it.readText() }
}

private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

// ---------------------------------------------------------------- speed limit (OpenStreetMap)

object SpeedLimit {
    private val _limit = MutableStateFlow<Int?>(null)
    val limit: StateFlow<Int?> = _limit.asStateFlow()
    private var lastQuery: Location? = null
    private var lastAt = 0L
    private var busy = false
    private var lastBeep = 0L

    fun beepEnabled(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("limit_beep", false)
    fun setBeep(ctx: Context, on: Boolean) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putBoolean("limit_beep", on).apply()

    fun onFix(ctx: Context, loc: Location, speedKmh: Int) {
        val now = System.currentTimeMillis()
        val moved = lastQuery?.distanceTo(loc) ?: Float.MAX_VALUE
        if (!busy && (moved > 120f && now - lastAt > 8_000 || now - lastAt > 60_000)) {
            busy = true
            lastAt = now
            lastQuery = loc
            io.launch {
                runCatching { query(loc.latitude, loc.longitude) }.onSuccess { if (it != null) _limit.value = it }
                busy = false
            }
        }
        val l = _limit.value ?: return
        if (speedKmh > l + 3 && beepEnabled(ctx) && now - lastBeep > 15_000) {
            lastBeep = now
            runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70).startTone(ToneGenerator.TONE_PROP_BEEP2, 300) }
        }
    }

    private fun query(lat: Double, lon: Double): Int? {
        val q = "[out:json][timeout:8];way(around:25,$lat,$lon)[highway][maxspeed];out tags 3;"
        val j = JSONObject(get("https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(q, "UTF-8")))
        val els = j.optJSONArray("elements") ?: return null
        for (i in 0 until els.length()) {
            val v = els.getJSONObject(i).optJSONObject("tags")?.optString("maxspeed").orEmpty()
            parse(v)?.let { return it }
        }
        return null
    }

    private fun parse(v: String): Int? = when {
        v.isBlank() -> null
        v.contains("urban", true) -> 50
        v.contains("rural", true) -> 80
        v.contains("motorway", true) -> 130
        v.contains("walk", true) -> 20
        else -> v.takeWhile { it.isDigit() }.toIntOrNull()
    }
}

// ---------------------------------------------------------------- fuel prices (French government open data)

data class Station(val name: String, val city: String, val lat: Double, val lon: Double, val prices: Map<String, Double>, val distanceKm: Double)

object Fuel {
    val FUELS = listOf("e10" to "SP95-E10", "sp95" to "SP95", "sp98" to "SP98", "gazole" to "Gazole", "e85" to "E85", "gplc" to "GPL")

    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun fuel(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).getString("fuel", "e10")!!
    fun setFuel(ctx: Context, f: String) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putString("fuel", f).apply()

    fun refresh(ctx: Context) {
        val lat = Drive.lat
        val lon = Drive.lon
        val f = fuel(ctx)
        _status.value = "Recherche des stations…"
        io.launch {
            runCatching {
                val where = URLEncoder.encode("within_distance(geom, geom'POINT($lon $lat)', 8km)", "UTF-8")
                val body = get(
                    "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/" +
                        "prix-des-carburants-en-france-flux-instantane-v2/records?limit=60&where=$where",
                )
                val results = JSONObject(body).getJSONArray("results")
                val here = Location("").apply { latitude = lat; longitude = lon }
                (0 until results.length()).mapNotNull { i ->
                    val r = results.getJSONObject(i)
                    val g = r.optJSONObject("geom")
                    val sLat = g?.optDouble("lat") ?: r.optDouble("latitude") / 100000
                    val sLon = g?.optDouble("lon") ?: r.optDouble("longitude") / 100000
                    val prices = mutableMapOf<String, Double>()
                    r.keys().forEach { k ->
                        if (k.endsWith("_prix")) r.optDouble(k).takeIf { !it.isNaN() && it > 0 }?.let { prices[k.removeSuffix("_prix")] = it }
                    }
                    if (prices.isEmpty()) return@mapNotNull null
                    val there = Location("").apply { latitude = sLat; longitude = sLon }
                    Station(
                        name = r.optString("adresse").ifBlank { "Station" },
                        city = r.optString("ville"),
                        lat = sLat, lon = sLon, prices = prices,
                        distanceKm = here.distanceTo(there) / 1000.0,
                    )
                }.filter { f in it.prices }.sortedBy { it.prices[f] }
            }.onSuccess {
                _stations.value = it
                _status.value = if (it.isEmpty()) "Aucune station trouvée à moins de 8 km pour ce carburant." else null
            }.onFailure {
                _status.value = "Impossible de récupérer les prix (internet ?)."
            }
        }
    }
}

// ---------------------------------------------------------------- maintenance reminders (from the odometer + GPS km)

data class Maint(val key: String, val label: String, val everyKm: Int, val everyMonths: Int, val dueInKm: Int?, val dueDate: LocalDate?)

object Maintenance {
    private val ITEMS = listOf(
        Triple("vidange", "Vidange", 15_000 to 12),
        Triple("pneus", "Pneus (contrôle / permutation)", 10_000 to 12),
        Triple("freins", "Plaquettes de frein", 30_000 to 24),
        Triple("ct", "Contrôle technique", 0 to 24),
    )

    private fun p(ctx: Context) = ctx.getSharedPreferences("maint", Context.MODE_PRIVATE)

    /** Odometer = value entered by the user + km driven since (GPS). */
    fun odometer(ctx: Context): Int? {
        val base = p(ctx).getInt("odo_base", -1)
        if (base < 0) return null
        val gpsAtBase = p(ctx).getFloat("gps_at_base", 0f)
        return base + (Drive.totalKm(ctx) - gpsAtBase).toInt()
    }

    fun setOdometer(ctx: Context, km: Int) {
        p(ctx).edit().putInt("odo_base", km).putFloat("gps_at_base", Drive.totalKm(ctx).toFloat()).apply()
    }

    fun markDone(ctx: Context, key: String) {
        val odo = odometer(ctx) ?: 0
        p(ctx).edit().putInt("${key}_km", odo).putString("${key}_date", LocalDate.now().toString()).apply()
    }

    fun items(ctx: Context): List<Maint> {
        val odo = odometer(ctx)
        return ITEMS.map { (key, label, every) ->
            val (km, months) = every
            val doneKm = p(ctx).getInt("${key}_km", -1)
            val doneDate = p(ctx).getString("${key}_date", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            Maint(
                key, label, km, months,
                dueInKm = if (km > 0 && odo != null && doneKm >= 0) doneKm + km - odo else null,
                dueDate = doneDate?.plusMonths(months.toLong()),
            )
        }
    }

    /** Short alert for the home screen, or null. */
    fun alert(ctx: Context): String? = items(ctx).firstNotNullOfOrNull { m ->
        when {
            m.dueInKm != null && m.dueInKm <= 500 -> "${m.label} : ${if (m.dueInKm <= 0) "à faire" else "dans ${m.dueInKm} km"}"
            m.dueDate != null && !m.dueDate.isAfter(LocalDate.now().plusDays(30)) -> "${m.label} : avant le ${m.dueDate.dayOfMonth}/${m.dueDate.monthValue}"
            else -> null
        }
    }
}
