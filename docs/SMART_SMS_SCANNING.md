# Smart SMS scanning

## Date windows

Dashboard scans default to the last two calendar months. The Dashboard offers 7 days, 30 days, two months, three months, a custom inclusive date range, and all Inbox messages. Relative windows use the device `ZoneId`; custom end dates include the entire local day. Internally, a selected range is represented as `[start midnight, midnight after end)`, so daylight-saving changes do not assume every local day is 24 hours. Incomplete or reversed custom ranges are rejected.

`AndroidSmsDataSource` pushes the range into the `ContentResolver` selection (`date >= ? AND date < ?`) and keeps the projection limited to `_ID`, `ADDRESS`, `BODY`, and `DATE`. Rows are returned in ascending provider ID order, through a `Cursor` closed with `use`, on `Dispatchers.IO`. The all-messages selection omits the date predicates.

## Incremental processing and reconciliation

Each selected range is queried on every scan. This is deliberate: an ID-only high-water mark cannot discover a newly imported older row or an edit to an existing row. A Room `processed_sms` ledger stores only provider ID, provider date, a SHA-256 fingerprint, pipeline version, and whether extraction produced an offer. It stores no SMS body or sender address. Matching fingerprints and pipeline versions skip normalization, classification, extraction, and date resolution. A changed fingerprint, unseen provider ID, full rescan, or processing-pipeline version change runs those stages again.

The pipeline version is `PROCESSING_PIPELINE_VERSION` in `ScanSmsUseCase`; increment it when normalization, classification, extraction, or expiration behavior changes in a way that should refresh stored offers. The schema was advanced through migrations 7→8 (processing ledger) and 8→9 (resolved-sender fingerprint). Existing offers and user merchant corrections are preserved by migration.

Changed messages are upserted by source SMS ID. If an edited message no longer yields an offer, only that app-owned derived offer is deleted. After a complete range scan, IDs absent from the provider query are removed from the app ledger and derived offer table for that same range. Reduced ranges retain offers outside the selected range. Cancelling or failing a scan does not run range reconciliation; completed 50-row batches remain transactionally checkpointed and are safely skipped on the next run.

The Dashboard reports rows encountered, rows unchanged, offers inserted or updated, malformed rows, and the current processing phase. “Full rescan” means reprocess every row in the selected date window; choose “All messages” first to reprocess the full Inbox. Counts in the result are scan outcomes, while Dashboard active/expired counts describe stored offers by expiration status.

## Sender intelligence

Existing body-free sender profiles are retained when the selected date window changes. Ordinary scans use the saved profile snapshot and inspect rows after its provider-ID checkpoint for new evidence. Full offer rescans do not replace a broader saved profile snapshot with evidence from only the selected window. Current-message merchant evidence continues to take precedence during extraction. Profile resolution changes invalidate the offer-side resolution fingerprint and trigger offer reprocessing.

The current aggregate sender profile format does not keep a per-message evidence journal. An edited or newly imported row with an older provider ID is reprocessed as an offer, but it does not revise the historical sender aggregate until a profile-building pass sees it. Expanding a range does not erase sender evidence learned in an earlier range. A future phase can add body-free per-message sender evidence deltas if profile correction after old-row edits becomes necessary.

## Recovery and privacy

- Scanning stays foreground and cancellable; no WorkManager dependency was added.
- Each batch writes derived offers, fingerprints, and scan checkpoint in one Room transaction.
- Cursor work and parsing remain off the main thread; there is no Inbox-wide body list in production.
- Cancellation is rethrown, leaving committed batches recoverable and postponing reconciliation until a complete scan.
- The scan path does not log message content and does not send, edit, or delete provider SMS rows.
- The date range is a query optimization, not a privacy boundary: `READ_SMS` remains required for the selected Inbox rows.

## Verification scope

Range arithmetic tests cover month ends, custom ranges across a DST change, invalid ranges, and the all-messages choice. Query-bound tests verify the half-open provider selection and argument order. Synthetic scan integration tests cover repeated scans, changed SMS IDs, offer removal after edits, stale-row reconciliation, full rescans, malformed rows, permission failures, and cancellation. DAO migration and ledger tests use synthetic data only.
