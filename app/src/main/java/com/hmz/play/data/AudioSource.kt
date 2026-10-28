package com.hmz.play.data

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.hmz.play.MainActivity

/**
 * On this kind of head unit, the car's audio chip only lets Bluetooth music through while the
 * unit's own Bluetooth app is the "current source". When HMZ Play is in front, the sound is cut.
 * Fix: open that Bluetooth app for a moment (it becomes the source), then come back to HMZ Play.
 */
object AudioSource {
    private val main = Handler(Looper.getMainLooper())
    private const val PREF = "bt_music_pkg"

    @Volatile
    var doneThisBoot = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)

    /** Remember which app owns the car's Bluetooth music session (seen by MediaBridge). */
    fun remember(ctx: Context, pkg: String) {
        if (looksLikeBluetooth(ctx, pkg)) prefs(ctx).edit().putString(PREF, pkg).apply()
    }

    private fun looksLikeBluetooth(ctx: Context, pkg: String): Boolean {
        val label = runCatching {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault("")
        val s = "$pkg $label".lowercase()
        return ("bt" in s || "bluetooth" in s || "a2dp" in s) && "settings" !in s
    }

    /** The head unit's Bluetooth app: remembered from the music session, or found by name. */
    fun findPackage(ctx: Context): String? {
        prefs(ctx).getString(PREF, null)?.let { if (ctx.packageManager.getLaunchIntentForPackage(it) != null) return it }
        val pm = ctx.packageManager
        val launchers = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        return launchers.map { it.activityInfo.packageName }
            .filter { it != ctx.packageName && looksLikeBluetooth(ctx, it) }
            .sortedByDescending { p -> listOf("music", "media", "audio").count { it in p.lowercase() } }
            .firstOrNull()
    }

    /** Makes Bluetooth the active audio source, then brings HMZ Play back. Returns false if no BT app found. */
    fun activate(ctx: Context, then: (() -> Unit)? = null): Boolean {
        val app = ctx.applicationContext
        val pkg = findPackage(app) ?: return false
        val i = app.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        runCatching { app.startActivity(i) }.onFailure { return false }
        main.postDelayed({
            runCatching {
                app.startActivity(
                    Intent(app, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION,
                    ),
                )
            }
            then?.let { main.postDelayed(it, 600) }
        }, 900)
        return true
    }
}
