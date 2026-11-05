package com.hmz.play.data

import android.content.Context
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
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Something that brings people (and rides): a train, a flight, an event. */
data class LiveItem(
    val kind: Kind,
    val time: LocalDateTime,
    val title: String,      // "TGV 7843 de Paris-Nord" / "Vol V7 1234 de Nice" / "Concert X"
    val place: String,      // "Lille-Europe" / "Aéroport Lille-Lesquin" / "Zénith de Lille"
    val detail: String = "",// "+10 min" / "à l'heure" / "fin 23:00"
    val big: Boolean = false,
    val lat: Double? = null,
    val lon: Double? = null,
    val end: LocalDateTime? = null,
) { enum class Kind { TRAIN, FLIGHT, EVENT } }

/**
 * Trains (SNCF API, free key), flights at Lille-Lesquin (AirLabs, free key) and events around the
 * MEL (OpenAgenda public dataset, no key). Refreshed in the background; keys stay on the head unit.
 */
object LiveInfo {
    private val _items = MutableStateFlow<List<LiveItem>>(emptyList())
    val items: StateFlow<List<LiveItem>> = _items.asStateFlow()
    private val _status = MutableStateFlow<Map<LiveItem.Kind, String>>(emptyMap())
    val status: StateFlow<Map<LiveItem.Kind, String>> = _status.asStateFlow()

    private var started = false
    private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** SNCF stop areas: Lille-Europe, Lille-Flandres. */
    private val STATIONS = listOf("stop_area:SNCF:87223263" to "Lille-Europe", "stop_area:SNCF:87286005" to "Lille-Flandres")

    /** Venues where an event means crowds (and traffic) around the MEL. */
    private val BIG_VENUES = listOf(
        "pierre-mauroy", "pierre mauroy", "decathlon arena", "zénith", "zenith", "grand palais", "stade bollaert",
        "aéronef", "aeronef", "sébastopol", "sebastopol", "opéra de lille", "théâtre du nord", "le colisée",
        "stab vélodrome", "lille grand palais", "braderie", "marché de noël", "stade lille métropole",
        "splendid", "zéphyr", "zephyr", "tripostal", "casino barrière", "kursaal", "la condition publique",
        "le grand bleu", "théâtre de l'idéal", "colisée", "flow -", "gare saint sauveur",
    )

    private val NOISE = listOf(
        "agence ", "france travail", "recrutement", "recrute", "réunion d'information", "information collective",
        "rendez-vous inscription", "atelier", "permanence", "formation", "poei", "poec", "job dating", "forum de l'emploi",
        "duoday", "bilan prévention", "mission locale", "crèche", "médiathèque", "bibliothèque",
    )

    private fun p(ctx: Context) = ctx.getSharedPreferences("live", Context.MODE_PRIVATE)
    fun sncfKey(ctx: Context) = p(ctx).getString("sncf", "").orEmpty()
    fun airKey(ctx: Context) = p(ctx).getString("airlabs", "").orEmpty()
    fun setKeys(ctx: Context, sncf: String, air: String) {
        p(ctx).edit().putString("sncf", sncf.trim()).putString("airlabs", air.trim()).apply()
        refreshNow()
    }

    fun start(ctx: Context) {
        if (started) return
        started = true
        app = ctx.applicationContext
        LiveAlerts.start(ctx.applicationContext, scope)
        scope.launch {
            var n = 0
            while (isActive) {
                // AirLabs free plan = 1000 requests/month: flights once an hour, never at night (~540/month).
                val h = LocalDateTime.now().hour
                refresh(flights = n % 4 == 0 && h in 6..23)
                n++
                delay(15 * 60_000L)
            }
        }
    }

    fun refreshNow() { scope.launch { refresh(flights = true) } }

    private suspend fun refresh(flights: Boolean) {
        val ctx = app ?: return
        val st = _status.value.toMutableMap()
        val keep = _items.value.filter { !flights && it.kind == LiveItem.Kind.FLIGHT }.toMutableList()
        val out = mutableListOf<LiveItem>()

        runCatching { trains(ctx) }
            .onSuccess { out += it; st[LiveItem.Kind.TRAIN] = if (sncfKey(ctx).isBlank()) "Ajoute ta clé SNCF dans Réglages" else "" }
            .onFailure { st[LiveItem.Kind.TRAIN] = "Trains indisponibles (${msg(it)})" }
        if (flights) {
            runCatching { flights(ctx) }
                .onSuccess { out += it; st[LiveItem.Kind.FLIGHT] = if (airKey(ctx).isBlank()) "Ajoute ta clé AirLabs dans Réglages" else "" }
                .onFailure { st[LiveItem.Kind.FLIGHT] = "Vols indisponibles (${msg(it)})" }
        } else out += keep
        runCatching { events() }
            .onSuccess { out += it; st[LiveItem.Kind.EVENT] = "" }
            .onFailure { st[LiveItem.Kind.EVENT] = "Événements indisponibles (${msg(it)})" }
        runCatching { losc() }
            .onSuccess { out += it }
            .onFailure { st[LiveItem.Kind.EVENT] = (st[LiveItem.Kind.EVENT].orEmpty() + " Matchs LOSC indisponibles (${msg(it)})").trim() }

        val now = LocalDateTime.now().minusMinutes(15)
        _items.value = out.filter { it.time.isAfter(now) }.sortedBy { it.time }
        _status.value = st
    }

    /** Short reason without any URL (it could contain an API key). */
    private fun msg(t: Throwable) = when (t) {
        is java.net.UnknownHostException, is java.net.ConnectException -> "pas d'internet"
        is java.net.SocketTimeoutException -> "délai dépassé"
        is IllegalStateException -> t.message ?: "erreur"
        else -> "réponse illisible"
    }

    private fun get(url: String, auth: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "HMZPlay/1.0")
        if (auth != null) c.setRequestProperty("Authorization", auth)
        val code = c.responseCode
        if (code == 401 || code == 403) error("clé refusée")
        if (code == 429) error("trop de demandes, réessai plus tard")
        if (code !in 200..299) error("serveur HTTP $code")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    // ---------------------------------------------------------------- trains (SNCF / Navitia)

    private fun trains(ctx: Context): List<LiveItem> {
        val key = sncfKey(ctx)
        if (key.isBlank()) return emptyList()
        val fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
        val out = mutableListOf<LiveItem>()
        for ((id, name) in STATIONS) {
            val url = "https://api.sncf.com/v1/coverage/sncf/stop_areas/$id/arrivals?count=25&data_freshness=realtime&duration=10800"
            val auth = "Basic " + android.util.Base64.encodeToString("$key:".toByteArray(), android.util.Base64.NO_WRAP)
            val arr = JSONObject(get(url, auth)).optJSONArray("arrivals") ?: continue
            for (i in 0 until arr.length()) {
                val a = arr.getJSONObject(i)
                val info = a.optJSONObject("display_informations") ?: continue
                val dt = a.optJSONObject("stop_date_time") ?: continue
                val real = runCatching { LocalDateTime.parse(dt.getString("arrival_date_time"), fmt) }.getOrNull() ?: continue
                val base = runCatching { LocalDateTime.parse(dt.getString("base_arrival_date_time"), fmt) }.getOrNull()
                val late = base?.let { java.time.Duration.between(it, real).toMinutes() } ?: 0
                val mode = info.optString("commercial_mode").ifBlank { "Train" }
                val num = info.optString("headsign")
                // Origin is not in "direction" for arrivals; show the line's direction/name when available.
                val from = info.optString("name").ifBlank { info.optString("direction") }
                val big = mode.contains("TGV", true) || mode.contains("Eurostar", true) || mode.contains("OUIGO", true) || mode.contains("INOUI", true)
                out += LiveItem(
                    LiveItem.Kind.TRAIN, real, "$mode $num".trim() + if (from.isNotBlank()) " · $from" else "",
                    name, if (late > 0) "+$late min" else "à l'heure", big,
                )
            }
        }
        return out
    }

    // ---------------------------------------------------------------- flights (AirLabs, Lille-Lesquin arrivals)

    private fun flights(ctx: Context): List<LiveItem> {
        val key = airKey(ctx)
        if (key.isBlank()) return emptyList()
        val j = JSONObject(get("https://airlabs.co/api/v9/schedules?arr_iata=LIL&api_key=" + URLEncoder.encode(key, "UTF-8")))
        j.optJSONObject("error")?.let { error(it.optString("message", "erreur")) }
        val arr = j.optJSONArray("response") ?: return emptyList()
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        return (0 until arr.length()).mapNotNull { i ->
            val f = arr.getJSONObject(i)
            val t = (f.optString("arr_estimated").ifBlank { f.optString("arr_time") })
            val time = runCatching { LocalDateTime.parse(t, fmt) }.getOrNull() ?: return@mapNotNull null
            val delay = f.optInt("arr_delayed", 0)
            LiveItem(
                LiveItem.Kind.FLIGHT, time,
                "Vol ${f.optString("flight_iata")} de ${f.optString("dep_iata")}",
                "Aéroport Lille-Lesquin",
                when {
                    f.optString("status") == "cancelled" -> "annulé"
                    delay > 0 -> "+$delay min"
                    else -> "à l'heure"
                },
                big = true, lat = 50.5633, lon = 3.0869,
            )
        }.distinctBy { it.title + it.time }
    }

    // ---------------------------------------------------------------- events (OpenAgenda public dataset)

    private fun events(): List<LiveItem> {
        val today = java.time.LocalDate.now()
        // Events whose first date is today or tomorrow (long-running expos/ateliers fill the list otherwise).
        val where = "within_distance(location_coordinates, geom'POINT(3.06 50.63)', 25km) " +
            "and firstdate_begin >= '$today' and firstdate_begin < '${today.plusDays(2)}'"
        val url = "https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/evenements-publics-openagenda/records" +
            "?limit=100&order_by=firstdate_begin&where=" + URLEncoder.encode(where, "UTF-8")
        val res = JSONObject(get(url)).optJSONArray("results") ?: return emptyList()
        val zone = ZoneId.systemDefault()
        return (0 until res.length()).mapNotNull { i ->
            val e = res.getJSONObject(i)
            val begin = runCatching {
                java.time.OffsetDateTime.parse(e.getString("firstdate_begin")).atZoneSameInstant(zone).toLocalDateTime()
            }.getOrNull() ?: return@mapNotNull null
            val endRaw = runCatching {
                java.time.OffsetDateTime.parse(e.getString("lastdate_end")).atZoneSameInstant(zone).toLocalDateTime()
            }.getOrNull()
            val place = listOf(e.optString("location_name"), e.optString("location_city")).filter { it.isNotBlank() }.joinToString(", ")
            val big = BIG_VENUES.any { place.lowercase().contains(it) || e.optString("title_fr").lowercase().contains(it) }
            val title = e.optString("title_fr")
            // job-centre sessions, workshops, info meetings: no crowd, no rides
            if (NOISE.any { title.lowercase().contains(it) || place.lowercase().contains(it) }) return@mapNotNull null
            val c = e.optJSONObject("location_coordinates")
            // Multi-day events (expos, tours…) only matter when they're big; their range covers every date,
            // so the evening's show is taken as starting at the first time and lasting about 2 h 30.
            var end = endRaw
            if (end != null && java.time.Duration.between(begin, end).toHours() > 12) {
                if (!big) return@mapNotNull null
                end = begin.plusMinutes(150)
            }
            LiveItem(
                LiveItem.Kind.EVENT, if (begin.toLocalDate().isBefore(today)) end ?: begin else begin,
                e.optString("title_fr").ifBlank { "Événement" }, place,
                end?.let { "jusqu'à %02d:%02d".format(it.hour, it.minute) } ?: "", big,
                c?.optDouble("lat")?.takeIf { !it.isNaN() }, c?.optDouble("lon")?.takeIf { !it.isNaN() }, end,
            )
        }
    }

    /** LOSC home matches at the stadium (TheSportsDB public key, no sign-up; free key = home matches only). */
    private fun losc(): List<LiveItem> {
        val j = JSONObject(get("https://www.thesportsdb.com/api/v1/json/123/eventsnext.php?id=133711"))
        val arr = j.optJSONArray("events") ?: return emptyList()
        val zone = ZoneId.systemDefault()
        val limit = LocalDateTime.now().plusDays(2)
        return (0 until arr.length()).mapNotNull { i ->
            val e = arr.getJSONObject(i)
            // the free feed also lists away matches: keep only matches played at home (Lille stadium)
            val home = e.optString("idHomeTeam") == "133711" || e.optString("strHomeTeam").contains("Lille", true)
            if (!home) return@mapNotNull null
            val ts = e.optString("strTimestamp").ifBlank { e.optString("dateEvent") + "T" + e.optString("strTime").ifBlank { "20:00:00" } }
            val utc = runCatching { java.time.OffsetDateTime.parse(ts) }
                .recoverCatching { java.time.LocalDateTime.parse(ts.take(19)).atOffset(java.time.ZoneOffset.UTC) }
                .getOrNull() ?: return@mapNotNull null
            val begin = utc.atZoneSameInstant(zone).toLocalDateTime()
            if (begin.isAfter(limit)) return@mapNotNull null
            val end = begin.plusMinutes(115)
            LiveItem(
                LiveItem.Kind.EVENT, begin,
                "⚽ " + e.optString("strEvent").ifBlank { "Match du LOSC" } + e.optString("strLeague").let { if (it.isBlank()) "" else " ($it)" },
                e.optString("strVenue").ifBlank { "Stade Pierre-Mauroy" } + ", Villeneuve-d'Ascq",
                "fin vers %02d:%02d".format(end.hour, end.minute), true, 50.6119, 3.1305, end,
            )
        }
    }

    /** Next thing worth knowing for the home screen (big ones first, within 90 min). */
    fun headline(list: List<LiveItem>): LiveItem? {
        val soon = LocalDateTime.now().plusMinutes(90)
        val now = LocalDateTime.now().minusMinutes(5)
        val window = list.filter { it.time.isAfter(now) && it.time.isBefore(soon) }
        return window.firstOrNull { it.big } ?: window.firstOrNull()
    }
}
