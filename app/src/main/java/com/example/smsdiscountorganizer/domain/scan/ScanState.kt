package com.example.smsdiscountorganizer.domain.scan

import com.example.smsdiscountorganizer.domain.sms.SmsCursor

data class ScanState(
    val cursor: SmsCursor?,
    val lastSuccessfulScanAtMillis: Long,
)
