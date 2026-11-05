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
    private var dock: Dock? = null
    private var callPanel: CallPanel? = null
    private var homes: Set<String> = emptySet()
    private var lastPkg: String? = null

    override fun onServiceConnected() {
        instance = this
        dock = Dock(this)
        callPanel = CallPanel(this).also { it.start() }
        homes = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0,
        ).map { it.activityInfo.packageName }.toSet() - packageName
    }

    override fun onUnbind(intent: Intent?): Boolean {
        dock?.release()
        dock = null
        callPanel?.release()
        callPanel = null
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == lastPkg) return
        when {
            pkg == packageName -> { lastPkg = pkg; dock?.hide() }
            pkg in homes -> { lastPkg = pkg; dock?.hide() }
            // Ignore system bars, keyboards, dialogs: they don't change the app on screen.
            packageManager.getLaunchIntentForPackage(pkg) == null -> Unit
            else -> {
                lastPkg = pkg
                Shell.record(this, pkg)
                refresh(pkg)
            }
        }
    }

    /** Called by HMZ Play when its own visibility changes. */
    fun refresh(pkg: String? = lastPkg) {
        val p = pkg ?: return
        if (Shell.hmzVisible || p == packageName || p in homes) dock?.hide() else dock?.show(p)
    }

    fun showDockFor(pkg: String) {
        lastPkg = pkg
        Handler(Looper.getMainLooper()).postDelayed({ refresh(pkg) }, 350)
    }

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

    /** Process-wide: true once Waze was auto-opened since the head unit started. */
    @Volatile
    var autoOpenedThisBoot = false

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
        addRecent(ctx, destination.trim())
        openNavigation(
            ctx,
            intents = listOf(
                // waze:// works on every Waze version (5.9.5.4 included); waze.com/ul as second choice.
                Intent(Intent.ACTION_VIEW, Uri.parse("waze://?q=$q&navigate=yes")).setPackage(WAZE),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?q=$q&navigate=yes")).setPackage(WAZE),
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")),
            ),
        )
    }

    /** Guidance to exact coordinates (fuel station, station, airport…). */
    fun navigateToCoords(ctx: Context, lat: Double, lon: Double, label: String) {
        if (lat.isNaN() || lon.isNaN()) { navigateTo(ctx, label); return }
        val ll = "%.6f,%.6f".format(java.util.Locale.US, lat, lon)
        openNavigation(
            ctx,
            intents = listOf(
                Intent(Intent.ACTION_VIEW, Uri.parse("waze://?ll=$ll&navigate=yes")).setPackage(WAZE),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?ll=$ll&navigate=yes")).setPackage(WAZE),
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:$ll?q=$ll(${Uri.encode(label)})")),
            ),
        )
    }

    private const val WAZE = "com.waze"

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
    fun openNavigation(ctx: Context, pkg: String? = null, intent: Intent? = null, intents: List<Intent>? = null) {
        val candidates = intents ?: listOfNotNull(intent ?: run {
            val p = pkg ?: navPackage(ctx) ?: run { Apps.openNavigation(ctx); return }
            ctx.packageManager.getLaunchIntentForPackage(p) ?: run { Apps.openNavigation(ctx); return }
        })
        val pm = ctx.packageManager
        // first candidate that an installed app can actually open
        val launch = candidates.firstOrNull { it.resolveActivity(pm) != null }
            ?: run { Apps.openNavigation(ctx); return }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val activity = ctx as? Activity
        if (activity != null && activity.isInMultiWindowMode) {
            // Already split: put the navigation app in the other half.
            launch.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            runCatching { ctx.startActivity(launch) }
            return
        }

        val svc = SplitService.instance
        if (svc == null) {
            // Without the accessibility service: ask Android 9 directly to open the app in split screen
            // (left/top half), then put HMZ Play in the other half.
            val opts = runCatching {
                val o = android.app.ActivityOptions.makeBasic()
                o.javaClass.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(o, 3) // SPLIT_SCREEN_PRIMARY
                o.toBundle()
            }.getOrNull()
            val started = opts != null && runCatching { ctx.startActivity(launch, opts) }.isSuccess
            if (!started && runCatching { ctx.startActivity(launch) }.isFailure) { Apps.openNavigation(ctx); return }
            if (started) {
                main.postDelayed({
                    // Only if Android really split the screen; otherwise leave Waze full screen.
                    if (!Shell.inSplit) {
                        android.widget.Toast.makeText(ctx, "Pour avoir Waze à côté de HMZ Play : Réglages HMZ › Barre CarPlay › Activer", android.widget.Toast.LENGTH_LONG).show()
                        return@postDelayed
                    }
                    runCatching {
                        ctx.startActivity(
                            Intent(ctx, MainActivity::class.java).addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT,
                            ),
                        )
                    }
                }, 900)
            } else {
                android.widget.Toast.makeText(ctx, "Pour avoir Waze à côté de HMZ Play : Réglages HMZ › Barre CarPlay › Activer", android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }
        if (runCatching { ctx.startActivity(launch) }.isFailure) { Apps.openNavigation(ctx); return }

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
