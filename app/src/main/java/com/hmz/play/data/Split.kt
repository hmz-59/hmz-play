package com.hmz.play.data

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
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

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)
    fun autoOpen(ctx: Context) = prefs(ctx).getBoolean("auto_nav", true)
    fun setAutoOpen(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("auto_nav", on).apply()

    /**
     * Opens the navigation app (Waze first) on the left half and HMZ Play on the right half.
     * Falls back to full screen when the tablet can't do split screen.
     */
    fun openNavigation(ctx: Context, pkg: String? = null) {
        val p = pkg ?: navPackage(ctx) ?: run { Apps.openNavigation(ctx); return }
        val launch = ctx.packageManager.getLaunchIntentForPackage(p) ?: run { Apps.openNavigation(ctx); return }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val activity = ctx as? Activity
        if (activity != null && activity.isInMultiWindowMode) {
            // Already split: put the navigation app in the other half.
            launch.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
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
