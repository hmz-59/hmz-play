package com.hmz.play.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** User choices shown in the UI (theme, wallpaper, app order). Compose-observable. */
object Prefs {
    enum class ThemeMode { AUTO, DAY, NIGHT }

    var theme by mutableStateOf(ThemeMode.AUTO)
        private set
    var wallpaper by mutableStateOf<Bitmap?>(null)
        private set
    var appOrder by mutableStateOf<List<String>>(emptyList())
        private set

    private var app: Context? = null
    private fun p() = app!!.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (app != null) return
        app = ctx.applicationContext
        theme = runCatching { ThemeMode.valueOf(p().getString("theme", "AUTO")!!) }.getOrDefault(ThemeMode.AUTO)
        appOrder = p().getString("app_order", "").orEmpty().split(',').filter { it.isNotBlank() }
        wallpaper = runCatching { BitmapFactory.decodeFile(wallFile().path) }.getOrNull()
    }

    fun setThemeMode(m: ThemeMode) {
        theme = m
        p().edit().putString("theme", m.name).apply()
    }

    fun setOrder(list: List<String>) {
        appOrder = list
        p().edit().putString("app_order", list.joinToString(",")).apply()
    }

    private fun wallFile() = File(app!!.filesDir, "wallpaper.jpg")

    /** Copies the picked picture, downscaled for a fast, soft-focus background. */
    fun setWallpaper(ctx: Context, uri: Uri?) {
        if (uri == null) {
            wallFile().delete()
            wallpaper = null
            return
        }
        val bmp = runCatching {
            ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return
        val w = 640
        val h = (bmp.height * w / bmp.width.toFloat()).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bmp, w, h, true)
        runCatching { wallFile().outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
        wallpaper = small
    }
}
