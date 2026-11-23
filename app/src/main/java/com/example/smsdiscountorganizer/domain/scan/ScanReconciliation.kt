package com.example.smsdiscountorganizer.domain.scan

/** Provider IDs observed in a completed bounded query; absent derived rows can then be reconciled. */
data class ScanReconciliation(val dateRange: SmsDateRange?, val observedSmsIds: Set<Long>)
