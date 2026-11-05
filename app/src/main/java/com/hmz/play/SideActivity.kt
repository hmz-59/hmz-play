package com.hmz.play

import android.os.Bundle

/**
 * HMZ Play in the right half of the split screen, next to Waze.
 * Android 9 never puts the home screen (MainActivity is the launcher) into split screen, so the
 * split uses this ordinary copy of the same interface. If the screen isn't split, it closes at once
 * and Waze stays full screen.
 */
class SideActivity : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isInMultiWindowMode) finish()
    }
}
