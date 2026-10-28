package com.hmz.play.data

import android.content.Context
import android.content.Intent

/** Launching apps "inside" HMZ Play (CarPlay-like): every app opens with the HMZ side bar. */
object Shell {
    /** True while an HMZ Play screen is visible (full screen or split); the dock hides then. */
    @Volatile
    var hmzVisible = false

    private const val KEY = "recent_apps"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun recents(ctx: Context): List<String> =
        prefs(ctx).getString(KEY, "").orEmpty().split(',').filter { it.isNotBlank() }

    fun record(ctx: Context, pkg: String) {
        if (pkg == ctx.packageName) return
        val list = (listOf(pkg) + recents(ctx).filterNot { it == pkg }).take(6)
        prefs(ctx).edit().putString(KEY, list.joinToString(",")).apply()
    }

    /** Opens an app. Navigation apps go to split screen next to HMZ Play; others full screen with the dock. */
    fun open(ctx: Context, pkg: String) {
        record(ctx, pkg)
        if (Split.isNavigation(pkg)) {
            Split.openNavigation(ctx, pkg)
            return
        }
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { ctx.startActivity(i) }
        // Show the dock right away (don't wait for the window event).
        SplitService.instance?.showDockFor(pkg)
    }
}
