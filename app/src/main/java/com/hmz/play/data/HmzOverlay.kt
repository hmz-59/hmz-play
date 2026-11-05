package com.hmz.play.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.hmz.play.MainActivity
import com.hmz.play.ResultProxyActivity
import com.hmz.play.ui.CarRoot
import com.hmz.play.ui.HmzTheme

/**
 * "Son garanti" mode. This head unit only lets Bluetooth music through while its own Bluetooth app
 * is the app in front, and cuts it as soon as HMZ Play is in front. So HMZ Play is drawn as a layer
 * ON TOP of the Bluetooth app (accessibility overlay): the head unit still sees its Bluetooth app in
 * front and keeps the sound, the driver sees and uses HMZ Play exactly as before.
 */
@SuppressLint("ClickableViewAccessibility")
class HmzOverlay(private val svc: SplitService) :
    LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner, OnBackPressedDispatcherOwner, ActivityResultRegistryOwner {

    private val wm = svc.getSystemService(WindowManager::class.java)
    private val reg = LifecycleRegistry(this)
    private val ssrc = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = reg
    override val savedStateRegistry: SavedStateRegistry get() = ssrc.savedStateRegistry
    override val viewModelStore = ViewModelStore()
    override val onBackPressedDispatcher = OnBackPressedDispatcher()

    /** Voice search / picture picker: run through a tiny invisible activity, result sent back here. */
    override val activityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            val intent = contract.createIntent(svc, input)
            hide() // the voice / picker screen must be visible
            ResultProxyActivity.start(svc, intent) { code, data -> dispatchResult(requestCode, code, data) }
        }
    }

    var refreshKey by mutableIntStateOf(0)
    private var root: FrameLayout? = null
    var shown = false
        private set

    init {
        ssrc.performAttach()
        ssrc.performRestore(null)
        reg.currentState = Lifecycle.State.CREATED
    }

    private fun build(): FrameLayout {
        val owner = this
        val compose = ComposeView(svc).apply {
            setContent {
                HmzTheme {
                    CompositionLocalProvider(
                        LocalOnBackPressedDispatcherOwner provides owner,
                        LocalActivityResultRegistryOwner provides owner,
                    ) {
                        CarRoot(
                            locationGranted = true,
                            refreshKey = refreshKey,
                            onRequestPermissions = { openClassic() },
                        )
                    }
                }
            }
        }
        val frame = object : FrameLayout(svc) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP && onBackPressedDispatcher.hasEnabledCallbacks()) onBackPressedDispatcher.onBackPressed()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        frame.setViewTreeLifecycleOwner(owner)
        frame.setViewTreeSavedStateRegistryOwner(owner)
        frame.setViewTreeViewModelStoreOwner(owner)
        frame.setViewTreeOnBackPressedDispatcherOwner(owner)
        frame.addView(compose, FrameLayout.LayoutParams(-1, -1))
        return frame
    }

    fun show() {
        if (shown) return
        val r = root ?: build().also { root = it }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE,
        ).apply { softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING }
        runCatching { wm.addView(r, lp) }.onSuccess {
            shown = true
            reg.currentState = Lifecycle.State.RESUMED
            Shell.hmzVisible = true
            svc.refresh()
        }
    }

    fun hide() {
        if (!shown) return
        shown = false
        reg.currentState = Lifecycle.State.CREATED
        runCatching { wm.removeViewImmediate(root) }
        Shell.hmzVisible = false
    }

    /** The normal full-screen HMZ Play (first start, permissions). */
    private fun openClassic() {
        hide()
        SoundMode.suspendOnce = true
        runCatching {
            svc.startActivity(Intent(svc, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
    }

    fun release() {
        hide()
        reg.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/** When and how the "son garanti" mode runs. */
object SoundMode {
    /** Set to open the classic HMZ Play once (permission screens), without switching back to the overlay. */
    @Volatile var suspendOnce = false

    fun enabled(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).getBoolean("sound_mode", true)
    fun setEnabled(ctx: Context, v: Boolean) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putBoolean("sound_mode", v).apply()

    /** Usable right now: setting on, accessibility service running, the head unit's Bluetooth app known. */
    fun available(ctx: Context) = enabled(ctx) && SplitService.instance != null && AudioSource.findPackage(ctx) != null

    /**
     * Shows HMZ Play as a layer and puts the head unit's Bluetooth app in front underneath it.
     * Returns false if the mode can't run (then the normal HMZ Play stays).
     */
    fun enter(ctx: Context): Boolean {
        if (suspendOnce) { suspendOnce = false; return false }
        val svc = SplitService.instance ?: return false
        if (!enabled(ctx)) return false
        val pkg = AudioSource.findPackage(ctx) ?: return false
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        svc.overlay().show()
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        return runCatching { ctx.startActivity(i) }.onFailure { svc.overlay().hide() }.isSuccess
    }

    fun active() = SplitService.instance?.overlayShown() == true
}
