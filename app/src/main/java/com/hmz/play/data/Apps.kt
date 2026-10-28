package com.hmz.play.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(val label: String, val pkg: String, val icon: ImageBitmap)

object Apps {
    private val NAVIGATION = listOf("com.waze", "com.google.android.apps.maps", "net.osmand", "com.here.app.maps")

    /** Shown first, in this order, when installed. */
    private val FAVORITES = listOf(
        "com.waze",
        "com.google.android.apps.maps",
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "deezer.android.app",
        "com.amazon.mp3",
        "com.whatsapp",
        "com.google.android.youtube",
        "com.heetch.driver",
        "ee.mtakso.driver",
        "com.ubercab.driver",
    )

    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val px = (ctx.resources.displayMetrics.density * 80).toInt().coerceIn(96, 256)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { ri ->
                runCatching {
                    AppEntry(
                        label = ri.loadLabel(pm).toString(),
                        pkg = ri.activityInfo.packageName,
                        icon = iconBitmap(ri.loadIcon(pm), px).asImageBitmap(),
                    )
                }.getOrNull()
            }
            .sortedWith(
                compareBy<AppEntry> { FAVORITES.indexOf(it.pkg).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                    .thenBy { it.label.lowercase() },
            )
            .toList()
    }

    /** Renders adaptive icons full-bleed (no system mask) so they fill a rounded square like CarPlay. */
    private fun iconBitmap(d: Drawable, size: Int): Bitmap {
        if (d is AdaptiveIconDrawable) {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val inset = size / 4
            listOfNotNull(d.background, d.foreground).forEach { layer ->
                layer.setBounds(-inset, -inset, size + inset, size + inset)
                layer.draw(c)
            }
            return bmp
        }
        return d.toBitmap(size, size, Bitmap.Config.ARGB_8888)
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(i) }.isSuccess
    }

    fun openNavigation(ctx: Context) {
        if (NAVIGATION.any { launch(ctx, it) }) return
        // No navigation app installed: open Waze's page in the Play Store.
        val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.waze")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(store) }.isSuccess) return
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.waze")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
