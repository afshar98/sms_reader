package com.example.smsdiscountorganizer.engine.expiration

import java.time.Clock
import java.time.Duration
import java.time.ZoneId

/** Uses a 24-hour default window to flag near-term expirations. */
class ExpirationCalculator(
    private val clock: Clock,
    private val zoneId: ZoneId,
    private val expiringSoonWindow: Duration = Duration.ofHours(24),
) {
    init {
        require(!expiringSoonWindow.isNegative) { "Expiring soon window must not be negative" }
    }

    fun status(resolution: ExpirationResolution?): ExpirationStatus {
        val expiry = (resolution as? ExpirationResolution.Resolved)?.expiresAt
            ?: return ExpirationStatus.UNKNOWN
        val now = clock.withZone(zoneId).instant()
        if (!expiry.isAfter(now)) return ExpirationStatus.EXPIRED
        return if (Duration.between(now, expiry) <= expiringSoonWindow) {
            ExpirationStatus.EXPIRING_SOON
        } else {
            ExpirationStatus.ACTIVE
        }
    }
}
