package com.hmz.play.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.hmz.play.ble.Cmd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaInfo(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationSec: Float = 0f,
    val playing: Boolean = false,
    val positionSec: Float = 0f,
    val positionAt: Long = 0L,
    val speed: Float = 1f,
    val art: Bitmap? = null,
    val source: String = "",
) {
    val isEmpty get() = title.isBlank() && artist.isBlank()
}

/**
 * Reads "now playing" from the media sessions already on the head unit — including the car's
 * own Bluetooth music app, which receives title/artist/cover from the iPhone (AVRCP).
 * Needs "Notification access" for HMZ Play (Android rule for reading media sessions).
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        MediaBridge.attach(this)
    }

    override fun onListenerDisconnected() {
        MediaBridge.detach()
    }
}

object MediaBridge {
    private val _info = MutableStateFlow(MediaInfo())
    val info: StateFlow<MediaInfo> = _info.asStateFlow()

    private var msm: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var appCtx: Context? = null

    val available get() = controller != null

    fun hasAccess(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners").orEmpty()
        return flat.contains(ctx.packageName)
    }

    fun openAccessSettings(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Called from the app too, so sessions are picked up even if the listener connected earlier. */
    fun start(ctx: Context) {
        if (msm != null || !hasAccess(ctx)) return
        attach(ctx.applicationContext)
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list.orEmpty())
    }

    internal fun attach(ctx: Context) {
        appCtx = ctx.applicationContext
        val m = ctx.getSystemService(MediaSessionManager::class.java) ?: return
        val cn = ComponentName(ctx, MediaListener::class.java)
        runCatching {
            if (msm == null) {
                m.addOnActiveSessionsChangedListener(sessionsListener, cn)
                msm = m
            }
            pick(m.getActiveSessions(cn))
        }
    }

    internal fun detach() {
        runCatching { msm?.removeOnActiveSessionsChangedListener(sessionsListener) }
        msm = null
        setController(null)
    }

    private fun pick(list: List<MediaController>) {
        val own = appCtx?.packageName
        val candidates = list.filter { it.packageName != own }
        val best = candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: candidates.firstOrNull { it.metadata != null }
            ?: candidates.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) setController(best)
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            setController(null)
            msm?.let { m -> appCtx?.let { runCatching { pick(m.getActiveSessions(ComponentName(it, MediaListener::class.java))) } } }
        }
    }

    private fun setController(c: MediaController?) {
        runCatching { controller?.unregisterCallback(callback) }
        controller = c
        runCatching { c?.registerCallback(callback) }
        if (c != null) appCtx?.let { AudioSource.remember(it, c.packageName) }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            _info.value = MediaInfo()
            return
        }
        val md = c.metadata
        val st = c.playbackState
        val label = runCatching {
            val pm = appCtx!!.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString()
        }.getOrDefault("")
        _info.value = MediaInfo(
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = (md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(),
            album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
            durationSec = ((md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L)) / 1000f,
            playing = st?.state == PlaybackState.STATE_PLAYING || st?.state == PlaybackState.STATE_BUFFERING,
            positionSec = ((st?.position ?: 0L).coerceAtLeast(0L)) / 1000f,
            positionAt = st?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = st?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            source = label,
        )
    }

    /** Returns true if handled by a media session. */
    fun command(cmd: Int): Boolean {
        val t = controller?.transportControls ?: return false
        when (cmd) {
            Cmd.PLAY -> t.play()
            Cmd.PAUSE -> t.pause()
            Cmd.TOGGLE -> if (_info.value.playing) t.pause() else t.play()
            Cmd.NEXT -> t.skipToNext()
            Cmd.PREVIOUS -> t.skipToPrevious()
            Cmd.VOLUME_UP -> controller?.adjustVolume(android.media.AudioManager.ADJUST_RAISE, 0)
            Cmd.VOLUME_DOWN -> controller?.adjustVolume(android.media.AudioManager.ADJUST_LOWER, 0)
        }
        if (cmd == Cmd.TOGGLE || cmd == Cmd.PLAY || cmd == Cmd.PAUSE) {
            // instant feedback; the real state follows from the session callback
            _info.value = _info.value.copy(playing = cmd == Cmd.PLAY || (cmd == Cmd.TOGGLE && !_info.value.playing))
        }
        return true
    }
}
