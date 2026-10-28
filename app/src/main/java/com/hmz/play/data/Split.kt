package com.hmz.play.data

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.hmz.play.MainActivity

/**
 * Accessibility service whose only job is the system "split screen" action. Android doesn't let a
 * normal app put another app in split screen, but an enabled accessibility service can.
 */
class SplitService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        var instance: SplitService? = null
    }
}

object Split {
    private val NAVIGATION = listOf("com.waze", "com.google.android.apps.maps", "app.organicmaps", "com.here.app.maps", "net.osmand")
    private val main = Handler(Looper.getMainLooper())

    fun isNavigation(pkg: String) = pkg in NAVIGATION

    fun navPackage(ctx: Context): String? =
        NAVIGATION.firstOrNull { ctx.packageManager.getLaunchIntentForPackage(it) != null }

    fun serviceEnabled(): Boolean = SplitService.instance != null

    fun openAccessibilitySettings(ctx: Context) {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /**
     * Starts guidance to a destination with a Waze deep link (free, official), in split screen.
     * Falls back to any maps app (geo: link) when Waze isn't installed.
     */
    fun navigateTo(ctx: Context, destination: String) {
        val q = Uri.encode(destination.trim())
        val waze = ctx.packageManager.getLaunchIntentForPackage("com.waze") != null
        val intent = if (waze) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?q=$q&navigate=yes")).setPackage("com.waze")
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q"))
        }
        addRecent(ctx, destination.trim())
        openNavigation(ctx, intent = intent)
    }

    fun recents(ctx: Context): List<String> =
        prefs(ctx).getString("recents", "").orEmpty().split('\n').filter { it.isNotBlank() }

    private fun addRecent(ctx: Context, d: String) {
        if (d.isBlank()) return
        val list = (listOf(d) + recents(ctx).filterNot { it.equals(d, ignoreCase = true) }).take(6)
        prefs(ctx).edit().putString("recents", list.joinToString("\n")).apply()
    }

    fun home(ctx: Context): String? = prefs(ctx).getString("fav_home", null)
    fun work(ctx: Context): String? = prefs(ctx).getString("fav_work", null)
    fun setHome(ctx: Context, v: String) = prefs(ctx).edit().putString("fav_home", v.trim()).apply()
    fun setWork(ctx: Context, v: String) = prefs(ctx).edit().putString("fav_work", v.trim()).apply()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)
    fun autoOpen(ctx: Context) = prefs(ctx).getBoolean("auto_nav", true)
    fun setAutoOpen(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("auto_nav", on).apply()

    /**
     * Opens the navigation app (Waze first) on the left half and HMZ Play on the right half.
     * Falls back to full screen when the tablet can't do split screen.
     */
    fun openNavigation(ctx: Context, pkg: String? = null, intent: Intent? = null) {
        val launch = intent ?: run {
            val p = pkg ?: navPackage(ctx) ?: run { Apps.openNavigation(ctx); return }
            ctx.packageManager.getLaunchIntentForPackage(p) ?: run { Apps.openNavigation(ctx); return }
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val activity = ctx as? Activity
        if (activity != null && activity.isInMultiWindowMode) {
            // Already split: put the navigation app in the other half.
            launch.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            runCatching { ctx.startActivity(launch) }
            return
        }

        val svc = SplitService.instance
        runCatching { ctx.startActivity(launch) }
        if (svc == null) return

        // Waze is now in front: split it (it becomes the left/top half), then bring HMZ Play into the other half.
        main.postDelayed({
            if (svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)) {
                main.postDelayed({
                    runCatching {
                        svc.startActivity(
                            Intent(svc, MainActivity::class.java).addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT,
                            ),
                        )
                    }
                }, 750)
            }
        }, 1300)
    }
}
