package com.seki.multilangduo

import android.app.Application

class MultilangDuoApplication : Application() {
    // The service and replacement Activities share the same task; no Activity is retained here.
    val playback: PlaybackController by lazy { PlaybackController(this) }
    override fun onTerminate() { playback.close(); super.onTerminate() }
}
