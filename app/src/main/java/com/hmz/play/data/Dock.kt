package com.hmz.play.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.hmz.play.MainActivity
import com.hmz.play.R
import com.hmz.play.ble.Cmd
import com.hmz.play.ble.IphoneLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CarPlay-style side bar shown over any app opened from HMZ Play: clock, iPhone status,
 * recent apps, play/pause and the home button. Drawn by the accessibility service as an
 * accessibility overlay (no extra permission needed).
 */
@SuppressLint("ClickableViewAccessibility")
class Dock(private val svc: SplitService) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var root: LinearLayout? = null
    private var shown = false
    private var shownFor: String? = null

    private val dp = svc.resources.displayMetrics.density
    private fun px(v: Int) = (v * dp).toInt()

    private lateinit var clock: TextView
    private lateinit var status: TextView
    private lateinit var apps: LinearLayout
    private lateinit var playIcon: ImageView

    private val tick = object : Runnable {
        override fun run() {
            if (!shown) return
            clock.text = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date())
            main.postDelayed(this, 1000 - System.currentTimeMillis() % 1000)
        }
    }

    fun show(foreground: String) {
        if (shown && shownFor == foreground) return
        if (root == null) build()
        shownFor = foreground
        fillRecents(foreground)
        if (!shown) {
            val lp = WindowManager.LayoutParams(
                px(84),
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.START or Gravity.TOP }
            runCatching { wm.addView(root, lp) }.onSuccess {
                shown = true
                root?.alpha = 0f
                root?.translationX = -px(40).toFloat()
                root?.animate()?.alpha(1f)?.translationX(0f)?.setDuration(180)?.start()
                main.post(tick)
                observe()
            }
        }
    }

    fun hide() {
        if (!shown) return
        shown = false
        shownFor = null
        job?.cancel()
        main.removeCallbacks(tick)
        runCatching { wm.removeViewImmediate(root) }
    }

    private fun observe() {
        job?.cancel()
        job = scope.launch {
            combine(IphoneLink.status, IphoneLink.battery, IphoneLink.track) { st, bat, tr -> Triple(st, bat, tr) }
                .collect { (st, bat, tr) ->
                    val connected = st == com.hmz.play.ble.LinkStatus.CONNECTED
                    status.text = if (connected) (bat?.let { "$it %" } ?: "iPhone") else "—"
                    status.setTextColor(if (connected) Color.WHITE else Color.parseColor("#8E8E93"))
                    playIcon.setImageResource(if (tr.playing) R.drawable.ic_dock_pause else R.drawable.ic_dock_play)
                    playIcon.alpha = if (connected && !tr.isEmpty) 1f else 0.4f
                }
        }
    }

    private fun build() {
        val ctx: Context = svc
        root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.BLACK)
            setPadding(0, px(14), 0, px(14))
        }
        clock = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
        }
        status = TextView(ctx).apply {
            textSize = 12f
            gravity = Gravity.CENTER
        }
        apps = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val music = tile(ctx, Color.parseColor("#FF375F")).also { f ->
            playIcon = ImageView(ctx).apply { setImageResource(R.drawable.ic_dock_play) }
            f.addView(playIcon, FrameLayout.LayoutParams(px(30), px(30), Gravity.CENTER))
            f.pressable { IphoneLink.command(Cmd.TOGGLE) }
        }
        val home = FrameLayout(ctx).apply {
            background = GradientDrawable().apply { cornerRadius = px(14).toFloat(); setColor(Color.parseColor("#1C1C1E")) }
            addView(
                ImageView(ctx).apply { setImageResource(R.drawable.ic_dock_home) },
                FrameLayout.LayoutParams(px(28), px(28), Gravity.CENTER),
            )
            pressable { goHome() }
        }
        root!!.apply {
            addView(clock, LinearLayout.LayoutParams(-1, -2))
            addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(2) })
            addView(View(ctx), LinearLayout.LayoutParams(1, 0, 1f))
            addView(apps, LinearLayout.LayoutParams(-2, -2))
            addView(music, LinearLayout.LayoutParams(px(56), px(56)).apply { topMargin = px(12) })
            addView(View(ctx), LinearLayout.LayoutParams(1, 0, 1f))
            addView(home, LinearLayout.LayoutParams(px(54), px(54)))
        }
    }

    private fun tile(ctx: Context, color: Int) = FrameLayout(ctx).apply {
        background = GradientDrawable().apply { cornerRadius = px(13).toFloat(); setColor(color) }
    }

    /** Recent apps, excluding the one on screen (it's already open). Shows up to 3. */
    private fun fillRecents(foreground: String) {
        val ctx: Context = svc
        apps.removeAllViews()
        Shell.recents(ctx).filter { it != foreground }.take(3).forEach { pkg ->
            val icon = runCatching { ctx.packageManager.getApplicationIcon(pkg) }.getOrNull() ?: return@forEach
            val iv = ImageView(ctx).apply {
                setImageBitmap(icon.toBitmap(px(56), px(56)))
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, px(13).toFloat())
                    }
                }
                pressable { Shell.open(ctx, pkg) }
            }
            apps.addView(iv, LinearLayout.LayoutParams(px(56), px(56)).apply { bottomMargin = px(12) })
        }
    }

    private fun goHome() {
        hide()
        runCatching {
            svc.startActivity(
                Intent(svc, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }
    }

    private fun View.pressable(onClick: () -> Unit) {
        isClickable = true
        setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            false
        }
        setOnClickListener { onClick() }
    }

    fun release() {
        hide()
        root = null
    }

    @Suppress("unused")
    private fun color(id: Int) = ContextCompat.getColor(svc, id)
}
