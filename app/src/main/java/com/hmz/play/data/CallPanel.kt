package com.hmz.play.data

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
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
import com.hmz.play.R
import com.hmz.play.ble.CarBluetooth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * HMZ Play call screen, drawn ABOVE the head unit's own Bluetooth call screen (accessibility
 * overlay: it doesn't take the audio focus or change the audio source, the call audio is untouched).
 * Incoming: name/number, Refuser / Accepter. In a call: timer + Raccrocher. Calling: Annuler.
 */
@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class CallPanel(private val svc: SplitService) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var root: FrameLayout? = null
    private var shown = false
    private val dp = svc.resources.displayMetrics.density
    private fun px(v: Int) = (v * dp).toInt()

    private lateinit var name: TextView
    private lateinit var status: TextView
    private lateinit var buttons: LinearLayout
    private var ring: ObjectAnimator? = null
    private lateinit var halo: View
    private var current: CarBluetooth.CallInfo? = null

    private val tick = object : Runnable {
        override fun run() {
            val c = current ?: return
            if (c.active) {
                val s = ((System.currentTimeMillis() - c.since) / 1000).toInt()
                status.text = "En cours · %d:%02d".format(s / 60, s % 60)
                main.postDelayed(this, 1000)
            }
        }
    }

    fun start() {
        job = scope.launch { CarBluetooth.call.collect { render(it) } }
    }

    fun release() {
        job?.cancel()
        hide()
    }

    private fun render(c: CarBluetooth.CallInfo?) {
        current = c
        if (c == null) { hide(); return }
        if (root == null) build()
        name.text = c.label
        if (c.name != null) name.text = "${c.name}\n${c.number}".trim()
        if (Vtc.active) name.text = "Appel" // passenger on board: no name or number on screen
        buttons.removeAllViews()
        main.removeCallbacks(tick)
        when {
            c.incoming -> {
                status.text = "Appel entrant · iPhone"
                buttons.addView(roundButton(R.drawable.ic_call_end, "#FF453A", "Refuser") { CarBluetooth.answer(false) })
                buttons.addView(space())
                buttons.addView(roundButton(R.drawable.ic_call, "#30D158", "Accepter") { CarBluetooth.answer(true) })
                pulse(true)
            }
            c.outgoing -> {
                status.text = "Appel en cours…"
                buttons.addView(roundButton(R.drawable.ic_call_end, "#FF453A", "Annuler") { CarBluetooth.hangUp() })
                pulse(true)
            }
            else -> {
                main.post(tick)
                buttons.addView(roundButton(R.drawable.ic_call_end, "#FF453A", "Raccrocher") { CarBluetooth.hangUp() })
                pulse(false)
            }
        }
        show()
    }

    private fun show() {
        if (shown) return
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        runCatching { wm.addView(root, lp) }.onSuccess {
            shown = true
            root?.alpha = 0f
            root?.animate()?.alpha(1f)?.setDuration(200)?.start()
        }
    }

    private fun hide() {
        main.removeCallbacks(tick)
        pulse(false)
        if (!shown) return
        shown = false
        runCatching { wm.removeViewImmediate(root) }
    }

    private fun build() {
        val ctx = svc
        root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.argb(235, 0, 0, 0))
            isClickable = true // block taps on the screen below
        }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(px(40), px(30), px(40), px(28))
            background = GradientDrawable().apply { cornerRadius = px(30).toFloat(); setColor(Color.parseColor("#1C1C1E")) }
        }
        val avatar = FrameLayout(ctx)
        halo = View(ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#30D158")) }
            alpha = 0f
        }
        val disc = FrameLayout(ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#2C2C2E")) }
            addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_person) }, FrameLayout.LayoutParams(px(60), px(60), Gravity.CENTER))
        }
        avatar.addView(halo, FrameLayout.LayoutParams(px(104), px(104), Gravity.CENTER))
        avatar.addView(disc, FrameLayout.LayoutParams(px(104), px(104), Gravity.CENTER))
        name = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 2
        }
        status = TextView(ctx).apply {
            setTextColor(Color.parseColor("#8E8E93"))
            textSize = 17f
            gravity = Gravity.CENTER
        }
        buttons = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        card.addView(avatar, LinearLayout.LayoutParams(px(140), px(140)))
        card.addView(name, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(10) })
        card.addView(status, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(4) })
        card.addView(buttons, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(26) })
        root!!.addView(card, FrameLayout.LayoutParams(px(560), -2, Gravity.CENTER))
    }

    private fun space() = View(svc).apply { layoutParams = LinearLayout.LayoutParams(px(80), 1) }

    private fun roundButton(icon: Int, color: String, label: String, onClick: () -> Unit): View {
        val ctx = svc
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val circle = FrameLayout(ctx).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(color)) }
                addView(ImageView(ctx).apply { setImageResource(icon) }, FrameLayout.LayoutParams(px(42), px(42), Gravity.CENTER))
                isClickable = true
                setOnTouchListener { v, e ->
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).start()
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
                    }
                    false
                }
                setOnClickListener { onClick() }
            }
            addView(circle, LinearLayout.LayoutParams(px(86), px(86)))
            addView(TextView(ctx).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 15f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(8) })
        }
    }

    private fun pulse(on: Boolean) {
        ring?.cancel()
        ring = null
        if (!::halo.isInitialized) return
        if (!on) { halo.alpha = 0f; return }
        val sx = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.35f)
        val sy = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.35f)
        val a = android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 0.5f, 0f)
        ring = ObjectAnimator.ofPropertyValuesHolder(halo, sx, sy, a).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }
}
