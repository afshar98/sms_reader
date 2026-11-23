# Synthetic scan performance report

## Scope and privacy

This report uses synthetic Persian SMS fixtures only. The device Inbox was not queried or scanned. The instrumentation benchmark uses an in-memory Room database and synthetic offer metadata; it does not access the Telephony provider.

Measurements are diagnostic single-run samples, not release guarantees. JVM pipeline measurements use the host JVM and an in-memory fake SMS source plus an in-memory fake scan repository. The Android persistence measurement ran on the attached Samsung SM-S711B (Android 16) and used an in-memory Room database.

## Findings and changes

- `PersianOfferExtractor` previously normalized a message, then re-normalized the same text in classification, merchant matching, discount parsing, and category extraction. Internal normalized-text entry points now reuse the first normalized string.
- `MerchantRegistry` and `ProductCategoryExtractor` previously built alias/term regular expressions during message matching. They now prepare those patterns once per extractor instance.
- Scan progress previously emitted a state update for each processed SMS. Updates now emit on the first message, every 100 messages, at least every 150 ms, and at save/checkpoint or terminal transitions.
- Ordinary scans seed the body-free profile builder from persisted profile aggregates and inspect only SMS IDs above the last checkpoint for new sender evidence.
- Incremental profile seeding retains only sender fingerprints, merchant evidence counts, and derived profile fields. It does not retain SMS bodies.
- Smart scanning now pushes a selected date range into the ContentResolver query and revisits provider rows in that range. A Room ledger compares per-message fingerprints and pipeline versions so unchanged rows skip the processing engine; edits and older-ID imports are discovered. Completed scans reconcile app-owned offers and ledger entries missing from the selected range.

## Synthetic pipeline results

The test warms up with 1,000 synthetic messages, then runs the complete scanner against 1,000, 10,000, and 30,000 messages. The fake source and repository isolate parsing and scan orchestration; Android provider and disk-backed database latency are not included here.

| Inbox rows | Before: duration | After engine optimization: duration | Smart scan: duration | Smart scan SMS/s | Offers | Progress events |
|---:|---:|---:|---:|---:|---:|---:|
| 1,000 | 645 ms | 208 ms | 217 ms | 4,601 | 51 | 43 |
| 10,000 | 5,857 ms | 1,752 ms | 1,796 ms | 5,567 | 501 | 426 |
| 30,000 | 17,058 ms | 4,638 ms | 4,569 ms | 6,564 | 1,501 | 1,209 |

The 30,000-row smart scan completed in 4,569 ms in the host fixture, close to the 4,638 ms previous optimized scan (about 1.5% faster in these single-run samples). The first processed-message progress arrived after 1,680 ms. The scan request-to-`isScanning` StateFlow update was 5 ms in this host run; this does not measure Activity launch or rendered-frame latency.

The first scan used two fake-source reads (profile evidence plus offer processing). A repeated no-change scan revisited the bounded fixture in two reads, checked all 30,000 fingerprints, skipped processing for all 30,000 rows, retained all 1,501 offers without duplicates, and took 205 ms. The fixture materializes synthetic rows; production still streams provider cursor rows.

## Stage samples

These stage timings are independently measured over the same synthetic fixture and overlap with the full scan; do not add them together.

| Stage | Sample size | Before | After |
|---|---:|---:|---:|
| Text normalization | 3,000 | 33 ms | 28 ms |
| Classification | 3,000 | 885 ms | 249 ms |
| Merchant recognition | 3,000 | 553 ms | 154 ms |
| Date resolution | 3,000 | 137 ms | 130 ms |
| Full sender profile build | 30,000 | 6,651 ms | 1,757 ms |
| Full offer extraction | 30,000 | 10,006 ms | 2,550 ms |

A separate warmed extractor comparison over 30,000 synthetic rows measured 9,054 ms before the optimization and 1,947 ms afterward, with 1,500 offers in each run. This is a 78.5% reduction for that fixture. The exact extraction rules and synthetic offer count remained unchanged.

## Device-side Room sample

On the attached SM-S711B running Android 16, an instrumented test wrote 1,500 synthetic offer rows through `RoomScanRepository.commitScan` in one transaction and read them through the Room `Flow` query:

- Transactional write: 594 ms
- Initial Flow query: 100 ms
- Rows read: 1,500
- Test result: passed

This used `Room.inMemoryDatabaseBuilder`; it does not represent durable flash storage, cold database opening, or production query latency. It does expose the current transactional upsert cost, including correction-preserving lookups.

## APK size

Current local build outputs measured with `stat`:

| Artifact | Size |
|---|---:|
| Debug APK | 65,488,363 bytes |
| Unsigned release APK | 47,990,907 bytes |

The previously recorded local artifacts were 65,461,879 bytes (debug) and 47,860,249 bytes (unsigned release). The current files are 26,484 bytes and 130,658 bytes larger respectively. This is a file-size comparison, not proof that the scan implementation alone caused the difference; no new library dependency was added.

## Memory, threading, and cancellation

- The host benchmark reported an 87 MiB peak JVM heap-pool reading at 30,000 rows. The fixture itself materializes 30,000 synthetic messages, so this is not a measurement of the Android app's Inbox memory footprint or PSS.
- Production Inbox access still streams `ContentResolver` cursor rows, closes the cursor with `use`, and uses `Dispatchers.IO`; extraction/profile work runs off the main thread and checks coroutine cancellation.
- Offer and scan-ledger writes remain in bounded batches of 50. Progress is bounded; the synthetic 30,000-message run emitted 1,209 progress events versus 30,622 before.
- Existing cancellation/checkpoint tests cover cancellation after a committed batch and ensure the next scan detects stale offer/profile state.
- The test profile builder verifies that seeding a saved aggregate and adding new synthetic SMS yields the same profile and fingerprint as a full build.

## Verification

Successful targeted host command:

```text
gradle --offline :app:testDebugUnitTest --tests '*SenderMerchantProfileBuilderTest' --tests '*ScanSmsPipelineIntegrationTest' --tests '*ScanPerformanceBenchmarkTest' --tests '*ExtractorPerformanceComparisonTest'
BUILD SUCCESSFUL
```

Successful Android-device command:

```text
gradle --offline :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.smsdiscountorganizer.data.local.ScanPersistencePerformanceTest
BUILD SUCCESSFUL; 1 synthetic Room instrumentation test passed on SM-S711B / Android 16
```

For smart scanning, the focused host command covering `ScanSmsPipelineIntegrationTest`, `ScanSmsUseCaseTest`, `SmsDateRangeSelectionTest`, `InboxQueryBoundsTest`, and `ScanPerformanceBenchmarkTest` passed. The benchmark produced the measurements above. The full `:app:testDebugUnitTest` suite ran 309 tests: 307 passed and two Room Robolectric test classes failed before their tests could start because the uncached `org.robolectric:android-all-instrumented:15-robolectric-14663667-2-i7` artifact could not be fetched: TLS certificate validation failed in this offline environment. On the connected SM-S711B (Android 16), the focused synthetic instrumentation run passed all 7 tests across `ProcessedSmsLedgerTest`, `MerchantCategoryPersistenceTest`, and `SenderMerchantProfilePersistenceTest`, including the Room 4→9 migration path. No SMS provider or device Inbox was queried.

## Remaining measurement gaps

- No real Inbox timing or messages were used, by design. ContentResolver/provider query latency for 1,000/10,000/30,000 actual rows is unmeasured.
- Android scan PSS/peak memory, CPU traces, and cold-start rendered-frame latency were not measured.
- The Room test uses an in-memory database; durable database performance and disk pressure remain to be measured with synthetic records on a production-like device setup.
- New SMS IDs are treated as append-only checkpoints. If a user deletes older SMS outside the app, the existing sender profile may retain its prior derived evidence until a full rescan rebuilds it.

## Recommended next step

Run a user-approved, on-device synthetic provider benchmark for cursor/provider latency and durable Room performance. Keep Inbox content out of benchmark logs and retain the current explicit full-rescan path for rebuilding sender profiles after external Inbox changes.
