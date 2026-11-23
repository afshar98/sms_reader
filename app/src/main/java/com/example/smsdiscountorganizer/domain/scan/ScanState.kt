package com.example.smsdiscountorganizer.domain.scan

import com.example.smsdiscountorganizer.domain.sms.SmsCursor

data class ScanState(
    val cursor: SmsCursor?,
    val lastSuccessfulScanAtMillis: Long,
    /** Sender snapshot used to process offers. Blank means no successful profile-aware scan yet. */
    val offersSenderProfileFingerprint: String = "",
    /** Most recently rebuilt sender snapshot, which may be newer than the offer snapshot. */
    val senderProfileFingerprint: String = "",
)
