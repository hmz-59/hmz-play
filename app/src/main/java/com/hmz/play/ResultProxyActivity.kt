package com.hmz.play

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle

/** Invisible helper: runs one "start for result" for the HMZ layer (voice search, picture picker). */
class ResultProxyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val target = pending ?: run { finish(); return }
        if (runCatching { startActivityForResult(target, 1) }.isFailure) {
            deliver(RESULT_CANCELED, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        deliver(resultCode, data)
    }

    private fun deliver(code: Int, data: Intent?) {
        val cb = callback
        pending = null
        callback = null
        active = false
        cb?.invoke(code, data)
        finish()
        overridePendingTransition(0, 0)
        // back to the HMZ layer over the Bluetooth app
        com.hmz.play.data.SoundMode.enter(applicationContext)
    }

    companion object {
        private var pending: Intent? = null
        private var callback: ((Int, Intent?) -> Unit)? = null
        @Volatile var active = false

        fun start(ctx: Context, intent: Intent, cb: (Int, Intent?) -> Unit) {
            pending = intent
            callback = cb
            active = true
            ctx.startActivity(Intent(ctx, ResultProxyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
    }
}
