package com.hmz.play.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Installer Waze" in one tap, on head units where the Play Store says Waze isn't compatible.
 *  1. Installs Aurora Store (open source, from the official F-Droid repository).
 *  2. Opens Waze in Aurora, which downloads the official Waze from Google Play's servers.
 */
object WazeInstaller {
    const val WAZE = "com.waze"
    private const val AURORA = "com.aurora.store"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()
    private var appCtx: Context? = null

    private fun say(msg: String) {
        _status.value = msg
        appCtx?.let { android.widget.Toast.makeText(it, msg, android.widget.Toast.LENGTH_LONG).show() }
    }

    fun wazeInstalled(ctx: Context) = installed(ctx, WAZE)
    private fun installed(ctx: Context, pkg: String) = ctx.packageManager.getLaunchIntentForPackage(pkg) != null

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        appCtx = app
        if (wazeInstalled(app)) { Apps.launch(app, WAZE); return }
        if (installed(app, AURORA)) { openWazeInAurora(app); return }
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
        scope.launch {
            say("Étape 1/2 : téléchargement d'Aurora Store…")
            val file = withContext(Dispatchers.IO) { runCatching { downloadAurora(app) }.getOrNull() }
            if (file == null) {
                say("Téléchargement impossible. Vérifie internet et réessaie.")
                return@launch
            }
            say("Étape 1/2 : installe Aurora Store, puis appuie de nouveau sur « Installer Waze ».")
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
            runCatching {
                app.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    private fun openWazeInAurora(ctx: Context) {
        say("Étape 2/2 : dans Aurora, choisis « Anonyme » si demandé, puis Installer.")
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$WAZE"))
            .setPackage(AURORA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(i) }.isFailure) Apps.launch(ctx, AURORA)
    }

    private fun downloadAurora(ctx: Context): File {
        val api = get(URL("https://f-droid.org/api/v1/packages/$AURORA")).bufferedReader().use { it.readText() }
        val code = JSONObject(api).getLong("suggestedVersionCode")
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "aurora.apk")
        get(URL("https://f-droid.org/repo/${AURORA}_$code.apk")).use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        require(out.length() > 500_000) { "fichier incomplet" }
        return out
    }

    private fun get(start: URL): java.io.InputStream {
        var url = start
        repeat(5) {
            val c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 10000
            c.readTimeout = 30000
            val code = c.responseCode
            if (code in 300..399) {
                url = URL(url, c.getHeaderField("Location"))
                c.disconnect()
            } else {
                return c.inputStream
            }
        }
        error("trop de redirections")
    }
}
