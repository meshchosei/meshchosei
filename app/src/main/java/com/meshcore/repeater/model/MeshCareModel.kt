package com.meshcore.repeater.model

enum class WatchdogDuration(val minutes: Int, val label: String) {
    FIVE_MINUTES(5, "5 Minuten"),
    TEN_MINUTES(10, "10 Minuten")
}

data class MeshCareState(
    val isActive: Boolean = false,
    val reducedTxPowerDbm: Double = 5.0, // Ziel-Drosselung (z. B. 5 dBm)
    val savedOriginalTxPowerDbm: Double = 14.0, // Reguläre Sendeleistung zum Wiederherstellen
    val duration: WatchdogDuration = WatchdogDuration.FIVE_MINUTES,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 300,
    val lastKeepAliveMillis: Long = 0L
) {
    val progress: Float
        get() = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds.toFloat() else 0f

    val formattedRemainingTime: String
        get() {
            val m = remainingSeconds / 60
            val s = remainingSeconds % 60
            return String.format("%02d:%02d", m, s)
        }
}
