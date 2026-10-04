package com.seki.multilangduo.model

data class PlaybackHistorySummary(val id: String, val playedAt: Long, val preview: String, val readable: Boolean = true)
data class PlaybackHistoryEntry(val id: String, val playedAt: Long, val text: String)
