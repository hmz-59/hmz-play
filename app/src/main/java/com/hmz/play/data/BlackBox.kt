package com.hmz.play.data

import android.content.Context
import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDate
import java.util.Locale

/**
 * Speed "black box": GPS time, position and speed every second, kept 30 days on the head unit only.
 * The "Flash ?" button marks a moment; later you can see the exact speed in the 30 s before it.
 */
data class FlashMark(val time: Long, val lat: Double, val lon: Double, val speedAtMark: Int, val maxBefore: Int, val samples: List<Pair<Int, Int>>)

object BlackBox {
    private const val KEEP_DAYS = 30L
    private var app: Context? = null
    private val buffer = StringBuilder()
    private var lastFlush = 0L
    private var lastWrite = 0L

    private val _marks = MutableStateFlow<List<FlashMark>>(emptyList())
    val marks: StateFlow<List<FlashMark>> = _marks.asStateFlow()

    private fun dir() = File(app!!.filesDir, "blackbox").apply { mkdirs() }
    private fun dayFile(d: LocalDate) = File(dir(), "$d.csv")

    fun start(ctx: Context) {
        if (app != null) return
        app = ctx.applicationContext
        // Old days are deleted automatically.
        val limit = LocalDate.now().minusDays(KEEP_DAYS).toString()
        dir().listFiles()?.filter { it.name.endsWith(".csv") && it.name != "marks.csv" && it.name < "$limit.csv" }?.forEach { it.delete() }
        loadMarks()
    }

    fun record(loc: Location, speedKmh: Int) {
        if (app == null) return
        val t = loc.time
        if (t - lastWrite < 900) return
        lastWrite = t
        buffer.append(t).append(',')
            .append("%.6f".format(Locale.US, loc.latitude)).append(',')
            .append("%.6f".format(Locale.US, loc.longitude)).append(',')
            .append(speedKmh).append('\n')
        if (t - lastFlush > 10_000) flush()
    }

    fun flush() {
        if (app == null || buffer.isEmpty()) return
        runCatching { dayFile(LocalDate.now()).appendText(buffer.toString()) }
        buffer.setLength(0)
        lastFlush = System.currentTimeMillis()
    }

    /** Marks "now" (tap right after a flash). */
    fun mark() {
        if (app == null) return
        flush()
        val now = System.currentTimeMillis()
        runCatching { File(dir(), "marks.csv").appendText("$now\n") }
        loadMarks()
    }

    fun deleteMark(time: Long) {
        val f = File(dir(), "marks.csv")
        runCatching { f.writeText(f.readLines().filter { it.trim() != time.toString() }.joinToString("\n", postfix = "\n")) }
        loadMarks()
    }

    private fun loadMarks() {
        val times = runCatching { File(dir(), "marks.csv").readLines().mapNotNull { it.trim().toLongOrNull() } }.getOrDefault(emptyList())
        _marks.value = times.sortedDescending().take(20).mapNotNull { analyze(it) }
    }

    /** Speed samples in the 30 s before the mark (seconds before → km/h). */
    private fun analyze(time: Long): FlashMark? {
        val day = java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val rows = runCatching {
            dayFile(day).readLines().mapNotNull { l ->
                val p = l.split(',')
                if (p.size < 4) null else Triple(p[0].toLong(), p[1].toDouble() to p[2].toDouble(), p[3].toInt())
            }
        }.getOrDefault(emptyList())
        val window = rows.filter { it.first in (time - 30_000)..time }
        if (window.isEmpty()) return FlashMark(time, 0.0, 0.0, -1, -1, emptyList())
        val last = window.last()
        val samples = window.filter { (time - it.first) / 1000 % 5 == 0L || it == last }
            .map { ((time - it.first) / 1000).toInt() to it.third }
            .distinctBy { it.first }
            .sortedBy { it.first }
        return FlashMark(time, last.second.first, last.second.second, last.third, window.maxOf { it.third }, samples)
    }
}
