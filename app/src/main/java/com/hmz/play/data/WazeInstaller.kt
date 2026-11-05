package com.hmz.play.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Waze on this head unit, done entirely on the head unit (no phone, no computer involved).
 *
 * Waze 5.9.90+ needs Android 10; this unit runs Android 9 (API 28), so the Play Store refuses it.
 * The last compatible release is 5.9.5.4 (Android 8.0+), published as an APK bundle (.apkm =
 * base APK + split APKs) on APKMirror, which only lists files signed with Google's own key.
 *  Step 1: open that page in the head unit's browser, tap download.
 *  Step 2: HMZ Play finds the file in Download/, checks it's Waze, and installs all its parts
 *          with Android's own installer (Android shows its standard install confirmation).
 */
object WazeInstaller {
    const val WAZE = "com.waze"
    const val PAGE = "https://www.apkmirror.com/apk/waze/waze-gps-maps-traffic-alerts-live-navigation/" +
        "waze-navigation-live-traffic-5-9-5-4-release/waze-navigation-live-traffic-5-9-5-4-android-apk-download/"
    private const val ACTION_DONE = "com.hmz.play.WAZE_INSTALL_DONE"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()
    private var appCtx: Context? = null

    private fun say(msg: String) {
        _status.value = msg
        appCtx?.let { android.widget.Toast.makeText(it, msg, android.widget.Toast.LENGTH_LONG).show() }
    }

    fun wazeInstalled(ctx: Context) = ctx.packageManager.getLaunchIntentForPackage(WAZE) != null

    /** Main button: open Waze, install the downloaded file, or open the download page. */
    fun start(ctx: Context) {
        val app = ctx.applicationContext
        appCtx = app
        if (wazeInstalled(app)) { Apps.launch(app, WAZE); return }
        if (findDownloaded() != null) { installDownloaded(ctx); return }
        openDownloadPage(app)
    }

    fun openDownloadPage(ctx: Context) {
        appCtx = ctx.applicationContext
        say("Étape 1/2 : touche « DOWNLOAD APK BUNDLE », attends la fin, puis reviens et appuie sur « Installer Waze ».")
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Newest Waze file in Download/ (.apkm bundle or plain .apk). */
    private fun findDownloaded(): File? {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return runCatching {
            dir.listFiles()?.filter {
                val n = it.name.lowercase()
                "waze" in n && (n.endsWith(".apkm") || n.endsWith(".apk")) && it.length() > 10_000_000
            }?.maxByOrNull { it.lastModified() }
        }.getOrNull()
    }

    fun installDownloaded(ctx: Context) {
        val app = ctx.applicationContext
        appCtx = app
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            say("Autorise HMZ Play à installer des applis, puis appuie de nouveau.")
            runCatching {
                app.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        val file = findDownloaded() ?: run {
            say("Fichier Waze introuvable dans Téléchargements. Fais d'abord l'étape 1.")
            return
        }
        scope.launch {
            say("Étape 2/2 : vérification et installation de Waze…")
            val err = withContext(Dispatchers.IO) { runCatching { install(app, file) }.exceptionOrNull() }
            if (err != null) say("Installation impossible : ${err.message}")
        }
    }

    private fun install(ctx: Context, file: File) {
        val parts = mutableListOf<Pair<String, () -> java.io.InputStream>>()
        val sizes = mutableListOf<Long>()
        val zip: ZipFile?
        if (file.name.lowercase().endsWith(".apk")) {
            zip = null
            parts += file.name to { file.inputStream() }
            sizes += file.length()
        } else {
            zip = ZipFile(file)
            val entries = zip.entries().toList().filter { !it.isDirectory && it.name.endsWith(".apk") }
            require(entries.isNotEmpty()) { "paquet vide ou illisible" }
            entries.forEach { e ->
                parts += e.name.substringAfterLast('/') to { zip.getInputStream(e) }
                sizes += e.size
            }
        }

        // Safety check: the bundle must really be Waze (package name read from the base APK).
        val base = File(ctx.cacheDir, "waze-check.apk")
        parts.first { it.first.startsWith("base") || parts.size == 1 }.second().use { i -> base.outputStream().use { i.copyTo(it) } }
        val info = ctx.packageManager.getPackageArchiveInfo(base.path, 0)
        base.delete()
        require(info?.packageName == WAZE) { "ce fichier n'est pas Waze" }

        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(WAZE) }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            parts.forEachIndexed { idx, (name, open) ->
                open().use { input ->
                    s.openWrite(name, 0, sizes[idx].takeIf { it > 0 } ?: -1).use { out ->
                        input.copyTo(out)
                        s.fsync(out)
                    }
                }
            }
            zip?.close()
            ContextCompat.registerReceiver(ctx, object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { c.startActivity(confirm) }
                        }
                        PackageInstaller.STATUS_SUCCESS -> {
                            say("Waze est installé ✔︎")
                            runCatching { c.unregisterReceiver(this) }
                        }
                        else -> {
                            say("Échec : ${i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "inconnu"}")
                            runCatching { c.unregisterReceiver(this) }
                        }
                    }
                }
            }, IntentFilter(ACTION_DONE), ContextCompat.RECEIVER_EXPORTED)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val cb = PendingIntent.getBroadcast(ctx, id, Intent(ACTION_DONE).setPackage(ctx.packageName), flags)
            s.commit(cb.intentSender)
        }
    }
}
