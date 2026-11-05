package com.hmz.play.data

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** A full-screen reminder: "3 TGV arrive at Lille-Europe in 30 min". */
data class LiveAlert(
    val kind: LiveItem.Kind,
    val headline: String,   // "ARRIVÉE TRAINS" / "ARRIVÉE AVION" / "SORTIE D'ÉVÉNEMENT"
    val at: LocalDateTime,  // exact time
    val place: String,
    val lines: List<String>,// up to 4 detail lines
    val lat: Double?, val lon: Double?,
    val minutesBefore: Int,
)

/**
 * Reminders before the big arrivals and events (60 / 30 / 10 min by default), so nothing is
 * missed even without looking at the Affluence screen. Trains of the same station within the
 * same half hour are grouped in one alert. Events remind before the start AND before the end
 * (that's when people need a ride home).
 */
object LiveAlerts {
    private val _current = MutableStateFlow<LiveAlert?>(null)
    val current: StateFlow<LiveAlert?> = _current.asStateFlow()
    private val queue = ArrayDeque<LiveAlert>()
    private val lock = Any()

    val ALL_OFFSETS = listOf(90, 60, 30, 15, 10)
    private fun p(ctx: Context) = ctx.getSharedPreferences("live", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = p(ctx).getBoolean("alerts", true)
    fun setEnabled(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("alerts", v).apply()
    fun offsets(ctx: Context): Set<Int> =
        p(ctx).getStringSet("offsets", setOf("60", "30", "10"))!!.mapNotNull { it.toIntOrNull() }.toSet()
    fun setOffsets(ctx: Context, v: Set<Int>) = p(ctx).edit().putStringSet("offsets", v.map { it.toString() }.toSet()).apply()
    fun sound(ctx: Context) = p(ctx).getBoolean("alert_sound", true)
    fun setSound(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("alert_sound", v).apply()

    private var app: Context? = null

    fun start(ctx: Context, scope: CoroutineScope) {
        app = ctx
        scope.launch {
            while (isActive) {
                runCatching { check(ctx) }
                delay(20_000)
            }
        }
    }

    fun dismiss() = synchronized(lock) {
        // skip reminders that are already past (e.g. queued while HMZ Play was hidden)
        var next = queue.removeFirstOrNull()
        while (next != null && next.at.isBefore(LocalDateTime.now().minusMinutes(2))) next = queue.removeFirstOrNull()
        _current.value = next
    }

    fun demo() = push(
        LiveAlert(
            LiveItem.Kind.TRAIN, "ARRIVÉE TRAINS", LocalDateTime.now().plusMinutes(30), "Lille-Europe",
            listOf("TGV INOUI 7843 · Paris-Nord · à l'heure", "Eurostar 9011 · Londres · +5 min"),
            50.6394, 3.0755, 30,
        ),
    )

    private fun push(a: LiveAlert) {
        synchronized(lock) { if (_current.value == null) _current.value = a else queue.addLast(a) }
        app?.let { if (sound(it)) beep() }
    }

    private fun beep() = runCatching {
        val t = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        t.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ t.release() }, 800)
    }

    private data class Group(val kind: LiveItem.Kind, val ids: List<String>, val at: LocalDateTime, val place: String,
                             val headline: String, val lines: List<String>, val lat: Double?, val lon: Double?)

    private fun groups(items: List<LiveItem>): List<Group> {
        val out = mutableListOf<Group>()
        // Trains: big ones only (TGV, Eurostar, OUIGO), grouped per station and half hour.
        items.filter { it.kind == LiveItem.Kind.TRAIN && it.big }
            .groupBy { it.place to bucket(it.time) }
            .forEach { (k, v) ->
                val s = v.sortedBy { it.time }
                out += Group(
                    LiveItem.Kind.TRAIN, s.map { "${it.time.toLocalDate()}|T|${it.place}|${it.title}" }, s.first().time, k.first,
                    if (s.size > 1) "${s.size} TRAINS ARRIVENT" else "ARRIVÉE TRAIN",
                    s.take(4).map { "%02d:%02d  ${it.title} · ${it.detail}".format(it.time.hour, it.time.minute) },
                    STATION_POS[k.first]?.first, STATION_POS[k.first]?.second,
                )
            }
        // Flights: grouped per half hour (Lille-Lesquin is small).
        items.filter { it.kind == LiveItem.Kind.FLIGHT && it.detail != "annulé" }
            .groupBy { bucket(it.time) }
            .forEach { (b, v) ->
                val s = v.sortedBy { it.time }
                out += Group(
                    LiveItem.Kind.FLIGHT, s.map { "${it.time.toLocalDate()}|F|${it.title}" }, s.first().time, "Aéroport Lille-Lesquin",
                    if (s.size > 1) "${s.size} AVIONS ATTERRISSENT" else "ARRIVÉE AVION",
                    s.take(4).map { "%02d:%02d  ${it.title} · ${it.detail}".format(it.time.hour, it.time.minute) },
                    50.5633, 3.0869,
                )
            }
        // Big events: before the start and before the end.
        items.filter { it.kind == LiveItem.Kind.EVENT && it.big }.forEach { e ->
            if (e.end == null || e.end != e.time) out += Group(LiveItem.Kind.EVENT, listOf("${e.time.toLocalDate()}|E|${e.title}|${e.time}"), e.time, e.place, "DÉBUT D'ÉVÉNEMENT",
                listOf(e.title, "Début %02d:%02d".format(e.time.hour, e.time.minute) + (e.end?.let { " · fin %02d:%02d".format(it.hour, it.minute) } ?: "")),
                e.lat, e.lon)
            val end = e.end
            if (end != null && Duration.between(e.time, end).toHours() <= 12) {
                out += Group(LiveItem.Kind.EVENT, listOf("${end.toLocalDate()}|X|${e.title}|$end"), end, e.place, "SORTIE D'ÉVÉNEMENT",
                    listOf(e.title, "Fin prévue %02d:%02d : beaucoup de monde va chercher un VTC".format(end.hour, end.minute)),
                    e.lat, e.lon)
            }
        }
        return out
    }

    private val STATION_POS = mapOf("Lille-Europe" to (50.6394 to 3.0755), "Lille-Flandres" to (50.6365 to 3.0707))

    private fun bucket(t: LocalDateTime) = "%s-%02d-%d".format(t.toLocalDate(), t.hour, t.minute / 30)

    private fun check(ctx: Context) {
        if (!enabled(ctx)) return
        val pr = p(ctx)
        // ids start with the item's date: keep yesterday and later, so nothing fires twice around midnight
        val keepFrom = LocalDate.now().minusDays(1).toString()
        val fired = pr.getStringSet("fired", emptySet())!!.filter { it.substringBefore('|') >= keepFrom }.toMutableSet()
        val now = LocalDateTime.now()
        val offs = offsets(ctx).sortedDescending()
        var changed = false
        for (g in groups(LiveInfo.items.value)) {
            val left = Duration.between(now, g.at).toMinutes()
            if (left < 0) continue
            // the smallest offset we've reached
            val off = offs.lastOrNull { left <= it } ?: continue
            if (g.ids.all { "$it|$off" in fired }) continue
            // mark this and every larger offset as done (no burst of old reminders)
            offs.filter { it >= off }.forEach { o -> g.ids.forEach { fired += "$it|$o" } }
            changed = true
            push(LiveAlert(g.kind, g.headline, g.at, g.place, g.lines, g.lat, g.lon, left.toInt()))
        }
        if (changed) pr.edit().putStringSet("fired", fired).apply()
    }
}
