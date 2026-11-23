package com.example.smsdiscountorganizer.domain.scan

data class ScanProgress(
    val isScanning: Boolean = false,
    val isFullRescan: Boolean = false,
    val processedMessages: Int = 0,
    val profiledMessages: Int = 0,
    val offersFound: Int = 0,
    val malformedMessages: Int = 0,
    val phase: ScanPhase = ScanPhase.IDLE,
)

enum class ScanPhase { IDLE, READING, PROFILING, PROCESSING, SAVING, COMPLETED, FAILED, CANCELLED }
