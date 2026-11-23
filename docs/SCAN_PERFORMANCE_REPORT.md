# Synthetic scan performance report

## Scope and privacy

This report uses synthetic Persian SMS fixtures only. The device Inbox was not queried or scanned. The instrumentation benchmark uses an in-memory Room database and synthetic offer metadata; it does not access the Telephony provider.

Measurements are diagnostic single-run samples, not release guarantees. JVM pipeline measurements use the host JVM and an in-memory fake SMS source plus an in-memory fake scan repository. The Android persistence measurement ran on the attached Samsung SM-S711B (Android 16) and used an in-memory Room database.

## Findings and changes

- `PersianOfferExtractor` previously normalized a message, then re-normalized the same text in classification, merchant matching, discount parsing, and category extraction. Internal normalized-text entry points now reuse the first normalized string.
- `MerchantRegistry` and `ProductCategoryExtractor` previously built alias/term regular expressions during message matching. They now prepare those patterns once per extractor instance.
- Scan progress previously emitted a state update for each processed SMS. Updates now emit on the first message, every 100 messages, at least every 150 ms, and at save/checkpoint or terminal transitions.
- Ordinary scans previously rebuilt sender profiles by reading the whole Inbox before each scan. They now seed the body-free profile builder from persisted profile aggregates and inspect only SMS IDs above the last checkpoint. A profile resolution change, stale offer/profile checkpoint, missing snapshot, or full rescan still causes a full offer reprocessing pass. A full rescan rebuilds sender profiles from the whole Inbox.
- Incremental profile seeding retains only sender fingerprints, merchant evidence counts, and derived profile fields. It does not retain SMS bodies.

## Synthetic pipeline results

The test warms up with 1,000 synthetic messages, then runs the complete scanner against 1,000, 10,000, and 30,000 messages. The fake source and repository isolate parsing and scan orchestration; Android provider and disk-backed database latency are not included here.

| Inbox rows | Before: duration | After: duration | Before: SMS/s | After: SMS/s | Offers (before / after) | Progress events after |
|---:|---:|---:|---:|---:|---:|---:|
| 1,000 | 645 ms | 208 ms | 1,548 | 4,797 | 51 / 51 | 43 |
| 10,000 | 5,857 ms | 1,752 ms | 1,707 | 5,704 | 501 / 501 | 399 |
| 30,000 | 17,058 ms | 4,638 ms | 1,758 | 6,468 | 1,501 / 1,501 | 1,204 |

At 30,000 messages, the measured end-to-end synthetic scan improved by 72.8%. The first processed-message progress arrived after 1,762 ms, versus 6,668 ms before the changes. The scan request-to-`isScanning` StateFlow update was 0 ms in this host run; this does not measure Activity launch or rendered-frame latency.

The old full scan used two fake-source reads. The repeated no-change scan now also uses two incremental reads after the saved ID, processes zero messages, and took 10 ms in the 30,000-row host fixture. The result retained all 1,501 offers without duplicates.

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

## Memory, threading, and cancellation

- The host benchmark reported an 87 MiB peak JVM heap-pool reading at 30,000 rows. The fixture itself materializes 30,000 synthetic messages, so this is not a measurement of the Android app's Inbox memory footprint or PSS.
- Production Inbox access still streams `ContentResolver` cursor rows, closes the cursor with `use`, and uses `Dispatchers.IO`; extraction/profile work runs off the main thread and checks coroutine cancellation.
- Offer writes remain in bounded batches of 50. Progress is now also bounded; the synthetic 30,000-message run emitted 1,203 progress events versus 30,622 before.
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

The full `:app:testDebugUnitTest` suite ran 299 tests: 297 passed and two Room Robolectric classes failed before their tests could start. Both failures are the same uncached `org.robolectric:android-all-instrumented:15-robolectric-14663667-2-i7` fetch, rejected by TLS certificate validation in this offline environment. A filtered rerun excluding only those two classes passed all 297 runnable tests. The synthetic Room instrumentation test passed on the connected device.

## Remaining measurement gaps

- No real Inbox timing or messages were used, by design. ContentResolver/provider query latency for 1,000/10,000/30,000 actual rows is unmeasured.
- Android scan PSS/peak memory, CPU traces, and cold-start rendered-frame latency were not measured.
- The Room test uses an in-memory database; durable database performance and disk pressure remain to be measured with synthetic records on a production-like device setup.
- New SMS IDs are treated as append-only checkpoints. If a user deletes older SMS outside the app, the existing sender profile may retain its prior derived evidence until a full rescan rebuilds it.

## Recommended next step

Run a user-approved, on-device synthetic provider benchmark for cursor/provider latency and durable Room performance. Keep Inbox content out of benchmark logs and retain the current explicit full-rescan path for rebuilding sender profiles after external Inbox changes.
