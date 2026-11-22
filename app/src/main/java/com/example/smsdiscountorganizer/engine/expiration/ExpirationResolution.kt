package com.example.smsdiscountorganizer.engine.expiration

import java.time.Instant

enum class ExpirationStatus {
    ACTIVE,
    EXPIRING_SOON,
    EXPIRED,
    UNKNOWN,
}

enum class DatePrecision {
    DATE,
    INSTANT,
}

enum class UnresolvedDateReason {
    NO_EXPIRATION,
    MISSING_REFERENCE_TIME,
    MISSING_YEAR,
    AMBIGUOUS_DATE,
    INVALID_DATE,
    UNSUPPORTED_DATE,
}

sealed interface ExpirationResolution {
    data class Resolved(
        val expiresAt: Instant,
        val precision: DatePrecision,
        val confidence: Double,
    ) : ExpirationResolution {
        init {
            require(confidence in 0.0..1.0)
        }
    }

    data class Unresolved(
        val reason: UnresolvedDateReason,
        val confidence: Double = 0.0,
    ) : ExpirationResolution {
        init {
            require(confidence in 0.0..1.0)
        }
    }
}
