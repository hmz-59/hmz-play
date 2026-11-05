package com.hmz.play.data

import android.app.Notification
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Next instruction / arrival from Waze's (or Maps') navigation notification, when it shows one. */
data class Guidance(val title: String, val text: String)

object NavInfo {
    private val _guidance = MutableStateFlow<Guidance?>(null)
    val guidance: StateFlow<Guidance?> = _guidance.asStateFlow()

    private val NAV = setOf("com.waze", "com.google.android.apps.maps")

    fun onPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in NAV) return
        val e = sbn.notification.extras
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        // Ignore Waze's generic "running in background" notice.
        val lower = (title + text).lowercase()
        if ("arrière-plan" in lower || "background" in lower) return
        _guidance.value = Guidance(title, text)
    }

    fun onRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName in NAV) _guidance.value = null
    }
}
