package com.hmz.play.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.hmz.play.BuildConfig
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

sealed interface UpdateState {
    data object Idle : UpdateState
    data object UpToDate : UpdateState
    data class Available(val version: String, val url: String) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/**
 * In-app updates: checks the public "hmz-play-update" repo (it only holds the APK) and installs
 * the new version with one tap. Android always asks the user to confirm the install.
 */
object Updater {
    private const val REPO = "hmz-59/hmz-play"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var lastCheck = 0L

    fun check(ctx: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < 30 * 60_000) return
        if (_state.value is UpdateState.Downloading) return
        lastCheck = now
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
                    c.connectTimeout = 8000
                    c.readTimeout = 8000
                    c.setRequestProperty("Accept", "application/vnd.github+json")
                    val body = c.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val tag = json.getString("tag_name") // e.g. v1.0.12
                    val code = tag.substringAfterLast('.').toIntOrNull() ?: 0
                    val assets = json.getJSONArray("assets")
                    var url: String? = null
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.getString("name").endsWith(".apk")) url = a.getString("browser_download_url")
                    }
                    Triple(tag, code, url)
                }
            }
            result.onSuccess { (tag, code, url) ->
                _state.value = if (code > BuildConfig.VERSION_CODE && url != null) {
                    UpdateState.Available(tag.removePrefix("v"), url)
                } else {
                    UpdateState.UpToDate
                }
            }.onFailure {
                if (force) _state.value = UpdateState.Failed("Pas de connexion internet ?")
            }
        }
    }

    fun install(ctx: Context) {
        val s = _state.value as? UpdateState.Available ?: return
        val app = ctx.applicationContext
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        scope.launch {
            _state.value = UpdateState.Downloading(0)
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(app.cacheDir, "updates").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val out = File(dir, "HMZ-Play-${s.version}.apk")
                    var url = URL(s.url)
                    var conn: HttpURLConnection
                    var hops = 0
                    while (true) {
                        conn = url.openConnection() as HttpURLConnection
                        conn.instanceFollowRedirects = false
                        conn.connectTimeout = 10000
                        conn.readTimeout = 20000
                        val code = conn.responseCode
                        if (code in 300..399 && hops++ < 5) {
                            url = URL(url, conn.getHeaderField("Location"))
                            conn.disconnect()
                            continue
                        }
                        break
                    }
                    val total = conn.contentLengthLong
                    conn.inputStream.use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(32 * 1024)
                            var done = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                done += n
                                if (total > 0) _state.value = UpdateState.Downloading((done * 100 / total).toInt())
                            }
                        }
                    }
                    out
                }.getOrNull()
            }
            if (file == null || file.length() < 100_000) {
                _state.value = UpdateState.Failed("Téléchargement interrompu, réessaie.")
                return@launch
            }
            _state.value = s
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
            runCatching {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { _state.value = UpdateState.Failed("Installation impossible : ${it.message}") }
        }
    }
}
