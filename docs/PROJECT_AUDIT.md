# SMS Discount Organizer — Implementation Audit

Audit date: 2026-11-22
Scope: full Android application source, Gradle configuration, tests, and merged release manifest. This is a read-only implementation audit; no implementation findings were changed.

## Executive summary

The repository contains a working, single-module Kotlin/Compose application with Hilt, Room, Flow-based repositories, a manual SMS scan path, Persian normalization/classification/extraction/date resolution, and state-backed dashboard/offers/details/settings/diagnostics screens. The processing engine itself has no Android imports. Release and debug APK assembly pass.

There is a **P0 startup-crash hazard** in the READ_SMS permission status path. The dashboard invokes that path during initial composition. It calls a restricted-permission allow-list API without handling its documented `SecurityException`; this is consistent with the immediate-close report on Android 16, although no device stack trace was available to prove it is the exception observed on that device. The app should not be considered ready for user validation until that path is checked and corrected.

Other material gaps are full-rescan stale-offer retention, broad relative-date interpretation, synthetic-only engine validation, no device tests, and whole-list UI state/filtering that will scale with the saved offer count.

## Implemented features

- **Application and navigation:** `SmsDiscountOrganizerApp` uses `@HiltAndroidApp`; `MainActivity` uses `@AndroidEntryPoint`, Compose, theme StateFlow, and RTL layout. `AppNavigation` connects Dashboard, Offers, Details, Settings, and SMS Diagnostics routes, with bottom navigation on compact widths and a rail on wider layouts.
- **MVVM/state:** Dashboard, Offers, Details, Settings, Theme, and Diagnostics each have ViewModels or screen-local permission state. Screens collect flows with lifecycle-aware Compose collection. Dashboard and Settings call the singleton scan use case; Details and Offers observe Room-backed offer state.
- **SMS source:** `AndroidSmsDataSource` checks READ_SMS, queries only the Inbox with `_ID`, `ADDRESS`, `BODY`, and `DATE`, uses a cursor closed by `use`, emits rows in ascending ID order, and runs the query on `Dispatchers.IO`. Incremental reads apply `_ID > cursor.smsId`; this is ID-based keyset continuation, not a `LIMIT`-sized page query.
- **Permission UI:** The app explains the READ_SMS request, uses the activity result permission API, handles rationale/permanent denial, offers app Settings, refreshes state on resume, and presents an installer-restricted message. Dashboard and Diagnostics each contain a permission presentation path.
- **Offline engine:** Pure Kotlin utilities normalize Persian/Arabic letters, digits, whitespace, zero-width characters, percentages, and currency tokens while preserving the source SMS object. Weighted rules classify discount, non-discount promotion, transactional, OTP, and unknown messages. Extractors produce structured fields. Jalali/Gregorian and relative date parsers accept an injected clock/zone and leave unresolved dates null.
- **Persistence/scan:** Room schema version 4 stores offer metadata, source SMS ID/timestamps, scan state, and preferences; it has ID uniqueness, a composite offer-order index, migration chain 1→2→3→4, Flow queries, and transactional scan commits. Scans are manual, streamed, serialized, checkpointed every 50 messages, cancellation-aware, and have a full-rescan path. Raw SMS body/address are not Room columns.
- **UI:** Dashboard metrics and recent offers, search/status/merchant/sort controls, detail presentation/coupon copy, theme selection, local data clearing, permission state, and Inbox diagnostics are connected to real application state. `PlaceholderContent` exists but has no call site.

## Missing, incomplete, or disconnected implementations

- **Dead production scaffolding:** `FakeOfferExtractor` is defined in `src/main` but is not the Hilt-provided extractor and has no production call site. `PlaceholderContent` is unused. `OfferRepository.upsertOffers()` has no production caller; scan persistence uses `ScanRepository.commitScan()` instead.
- **Diagnostic reasons do not reach the product flow:** `DiscountClassifier` returns static rule reasons, but `PersianOfferExtractor` retains only category/confidence. The reasons are neither part of `OfferMetadata` nor surfaced in the diagnostic UI. This is safe for privacy, but means the requested rule-level debugging path is not connected.
- **Mislabelled confidence:** `PersianOfferExtractor` assigns `classification.confidence` to `OfferMetadata.confidence`; Details labels it “extraction confidence”. It is a heuristic classification score, not a measured probability that each extracted field is correct.
- **Expiration metadata is narrowed:** The date engine separates precision and confidence, but extraction stores only `validUntilMillis`; the persisted `Offer` and UI lose date precision, unresolved reason, and date confidence. `ExpirationCalculator`'s `EXPIRING_SOON` result is not used by the UI, which independently implements `OfferStatus` with an `EXPIRING_TODAY` bucket.
- **One app module:** `core`, `data`, `domain`, `engine`, and features are package boundaries inside one Gradle module, not separately compiled modules. `ScanSmsUseCase` depends on the `engine.OfferExtractor` interface, so the domain package points at the engine package even though both remain Android-free.
- **Narrow deterministic extraction:** Merchant and title extraction rely on explicit labels; product category returns null if multiple category dictionaries match; amount/code patterns cover common formats rather than general Persian SMS language. These are conservative nulls but reduce recall. Current test input is synthetic, not a representative, redacted real-message corpus.
- **No background scan:** Scanning is manual from Dashboard/Settings. There is no scheduled or SMS-receiver-triggered scan. This is consistent with the current offline/manual workflow, but it is not a background scanning implementation.

## Critical bugs and high-risk behavior

### P0 — Permission status can crash app startup

`SmsPermissionManager.status()` calls `isReadSmsAllowlisted()` whenever READ_SMS is not already granted (`SmsPermissionManager.kt:15-24`). That function queries installer/system/upgrade restricted-permission allow-lists (`SmsPermissionManager.kt:42-54`). Android documents that system allow-list access is limited to preinstalled holders, installer/upgrade access is limited to authorized callers, and `getWhitelistedRestrictedPermissions()` throws `SecurityException` if the caller cannot access the requested list ([Android PackageManager reference](https://developer.android.com/reference/android/content/pm/PackageManager#getWhitelistedRestrictedPermissions(java.lang.String,int))). The app does not catch that exception. `SmsPermissionScanCard` calls `status()` from its initial `LaunchedEffect` (`SmsPermissionScanCard.kt:56-57`), and the same pattern exists in Diagnostics (`SmsDiagnosticsScreen.kt:64-68`). A normal APK-installed, ungranted app is likely to hit this path before showing the permission state. This is consistent with the reported immediate close, but confirmation requires an AndroidRuntime stack trace from the device.

**Recommended next action:** make permission status evaluation safe for an ordinary app process and test launch with READ_SMS ungranted on Android 16. Do not add privileged permissions, manipulate allow-lists, or bypass Android's hard restriction.

### P1 — Full rescan does not reconcile removed source SMS

Full rescan reprocesses Inbox rows and upserts by `sourceSmsId`, but never removes local offers whose source ID is absent (`ScanSmsUseCase.kt:64-80, 92-103, 142-150`; `RoomScanRepository.kt:24-29`). If a source SMS is removed from the device Inbox, the derived offer remains indefinitely and can continue to display as active. This is also a retention mismatch: stored derived data is not tied to the source message's continuing presence.

### P1 — Relative language outside an expiry clause can become an expiration

`PersianOfferExtractor` passes the substring beginning at the first expiry marker, but falls back to the entire SMS if there is no marker (`PersianOfferExtractor.kt:32, 62`). `RelativeDateParser` treats any occurrence of “امروز”, “فردا”, “پس فردا”, hour durations, “پایان هفته”, or “تا جمعه” as an expiry (`RelativeDateParser.kt:16-28`). A promotion start date or unrelated schedule can therefore be interpreted as its expiry. Missing and ambiguous dates are otherwise kept unresolved; UI `OfferStatus` maps null expiry to `UNKNOWN` (`OfferPresentation.kt:14-23`).

### P1 — Room-backed UI state and filtering materialize all offers

`OfferDao.observeAll()` returns `Flow<List<OfferEntity>>` (`OfferDao.kt:11-12`). Dashboard maps all offers to statuses and filters them in its combine collector; Offers filters/sorts the whole list in the ViewModel (`DashboardViewModel.kt:52-67`, `OffersViewModel.kt:40-65`, `OfferPresentation.kt:25-57`). Details also observes the entire list and linearly searches for one source ID (`DetailsViewModel.kt:21-23`). Inbox scanning is streaming and bounded to batches of 50, but those properties do not bound saved-offer/UI memory, and ViewModel list work runs in `viewModelScope` without an explicit CPU dispatcher. There is no Paging path.

### P2 — Permission-denial logic lacks platform-path tests

Only the pure status resolver is unit-tested. No test exercises the real PackageManager calls, activity rationale path, runtime result callback, resume refresh, hard-restricted installer denial, or app startup with permission denied. The P0 risk was not caught by tests.

### P2 — Clear-data action leaves permission-request history

Room clear deletes offers, scan state, and Room preferences, but `SmsPermissionManager` keeps `read_sms_request_attempted` in a separate `SharedPreferences` file. This bit is not SMS content and does not grant access, but “clear all local app data” does not clear all app-managed state until Android app-data clear/uninstall.

## Discount engine audit

- The production DI module binds `OfferExtractor` to `PersianOfferExtractor`, not `FakeOfferExtractor` (`OfferExtractorModule.kt`). The end-to-end scan test uses the real rule engine plus a fake SMS source and in-memory repository.
- Normalization is deterministic and Android-free. Coupon extraction is run on the original body to preserve code casing. Neither `Offer` nor `OfferEntity` contains the source body.
- Classifier rules prioritize OTP, transaction/payment, and negation categories above ordinary discount rules. The test fixture asserts at least 60 synthetic cases and at least 95% precision on that fixture (`DiscountClassifierTest.kt:32-54, 98+`). This is a fixture check only; it does not establish real-world precision.
- Extraction supports multiple percentage/fixed discount matches and excludes ranges recognized as minimum purchase. Merchant/title extraction is label-oriented, and category extraction deliberately yields null for multiple category matches. There is no real-world extraction benchmark or per-field precision/recall report.
- Date resolution handles Jalali/Gregorian and relative expressions with injected time/zone. Missing year, conflicting dates, invalid dates, missing reference time, and absent expiration remain unresolved. Null expiry is presented as `UNKNOWN`; it is not silently promoted to active.
- Date parsing is conservative in ambiguity but the caller's whole-message fallback described above creates a semantic false-expiration risk. Expiration precision/confidence are also discarded before persistence/display.

## Database and scan audit

- Schema v4 contains `app_preferences`, `offers`, and `scan_state`; offer primary key is `sourceSmsId`, and the composite `(sourceDateMillis, sourceSmsId)` index matches the main DAO ordering. `MIGRATION_1_2`, `MIGRATION_2_3`, and `MIGRATION_3_4` are registered. `exportSchema=false` means Room schema snapshots are not checked in.
- `RoomScanRepository.commitScan()` wraps offer upserts and scan-state upsert in `RoomDatabase.withTransaction`. `OfferDao.upsertAll()` is also annotated `@Transaction`. DAO Flow and count/lookup methods exist.
- Incremental scanning uses monotonically increasing SMS row ID, so equal message timestamps do not block later IDs. Repeated source IDs update the primary-key row; full rescans re-extract and update records. Cancellation preserves committed incremental checkpoints; full-rescan checkpoints remain at the old cursor until final success.
- **Stale rows:** no “seen IDs” reconciliation or local offer delete is part of full rescan. A stale offer survives until manually clearing local data.
- **Schema test gap:** tests cover migration paths starting from v1 and v2, but no isolated v3→v4 fixture exists. The current Room/Robolectric suite did not reach its assertions because Robolectric artifact initialization failed in this environment.
- **Retention:** metadata and scan state have no TTL. `clearAllLocalData()` transactionally clears the three Room tables. App backup is disabled in the manifest; Room is not application-encrypted.
- **Scan size:** ContentResolver emits one cursor row at a time and closes it safely. There is no provider `LIMIT` pagination; a single cursor remains open for a scan, with Room writes checkpointed every 50 rows. The repository has a 10,000-row synthetic Flow test, but it uses an in-memory repository and no-offer extractor; it does not measure ContentResolver, Room throughput, or thousands of persisted offers on a device.

## Security findings

- **READ_SMS only:** release merged manifest declares `android.permission.READ_SMS` and an AndroidX-generated, signature-protected `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No `INTERNET`, `SEND_SMS`, `RECEIVE_SMS`, `WRITE_SMS`, `READ_CONTACTS`, or `READ_CALL_LOG` permission was found.
- **Read-only:** source uses `Telephony.Sms.Inbox.CONTENT_URI` via ContentResolver. No Telephony SMS insert/update/delete/send path was found. Local Room `DELETE` statements only clear app-owned tables.
- **No body persistence/logging:** SMS body/address exist only on the in-memory `SmsMessage` at the platform boundary. Offer entities and domain persistence models include derived metadata/source ID/timestamps only. No application logging APIs or sensitive logging SDKs were found.
- **No network/cloud SDKs:** resolved release runtime dependencies are AndroidX Compose/Activity/Lifecycle/Navigation/Room, Kotlin Coroutines, and Hilt/Dagger. Focused search found no HTTP client, cloud, analytics, telemetry, or advertising dependency; no application network API use was found.
- **Backup:** merged release application has `allowBackup=false`.
- **Exported components:** launcher `MainActivity` is exported for `MAIN`/`LAUNCHER`; AndroidX `ProfileInstallReceiver` is exported but guarded by `android.permission.DUMP`. AndroidX Startup provider and Room invalidation service are `exported=false`. No default-SMS intent filters/components exist.
- **Release build:** merged manifest has no `debuggable=true`; `aapt dump badging` does not report `application-debuggable` for the release APK. Current Gradle release output is unsigned, as documented separately in `RELEASE_CHECKLIST.md`.
- **Permission API concern:** the uncaught restricted allow-list query is the high-priority runtime security/reliability finding above. Do not address it by broadening permissions or bypassing installer controls.

## Architecture issues

1. P0 permission status is called during initial screen composition, so a platform exception is in the app's startup path rather than isolated to the Scan button.
2. Source boundaries are packages, not Gradle modules. The core/data/domain/engine/features separation is readable, and the engine has no Android imports, but `domain.scan.ScanSmsUseCase` imports the `engine.OfferExtractor` port.
3. `OfferRepository.upsertOffers()` duplicates the persistence write surface while production scan writes through `ScanRepository.commitScan()`. It is currently unused.
4. `FakeOfferExtractor` and `PlaceholderContent` remain in production source but are not connected to app behavior.
5. Date status logic is split between `ExpirationCalculator` and UI `OfferPresentation.statusAt`, and the expiry metadata loses precision/confidence before it reaches Room/UI.
6. `SmsDiagnostics` uses a separate `ScanSmsInbox` full Inbox count flow rather than the main scan pipeline. It is a diagnostic scan, not offer processing.

## Test coverage gaps

- `src/test` contains 13 test files and the full local task reports 251 executions including parameterized cases. Coverage includes normalization, classification, extraction, expiration, scan pipeline fakes, Room DAO/repository behavior, status resolution, and offer presentation.
- Classifier tests have a synthetic precision threshold; there is no representative real-message corpus with a measured confusion matrix.
- `src/androidTest` contains one `OfferCardUiTest`; there are no UI tests for startup/Hilt, navigation as a graph, permission prompts/denials, dashboard scanning, clear-data flow, or actual AndroidSmsDataSource behavior.
- No instrumentation execution occurred because no device/emulator was attached; in this environment the ADB daemon also failed to start (`Operation not permitted`).
- Android permission/API behavior and actual Inbox cursor/device behavior remain untested on Android 16.
- Room/Robolectric tests are present, but two test classes failed before assertions because Robolectric could not download/initialize its Maven artifact due `SSLHandshakeException` / `PKIX path building failed`.
- Large scan coverage is synthetic Flow and does not exercise an actual provider cursor or Room with a large offer population.

## Security and release verification

| Check | Result |
| --- | --- |
| Release manifest | Inspected at `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`; only READ_SMS plus AndroidX's signature internal permission. |
| Prohibited permissions / SMS roles | No INTERNET, SMS write/send/receive, contacts, call-log, SMS receiver, or default SMS role behavior found. |
| Raw body persistence / logs | No raw-body entity/column or logging API/SDK use found. |
| Backup/exported/debuggable | `allowBackup=false`; launcher exported only for launcher intent; Profile Installer receiver has DUMP permission; private provider/service not exported; release APK non-debuggable. |
| Runtime SDKs/network code | Resolved runtime graph and source searches found no network/cloud/analytics/telemetry/ad SDKs or network API use. |
| READ_SMS behavior | Query is read-only Inbox via ContentResolver. Runtime permission flow is present but has the P0 allow-list API exception risk and has no platform test. |

## Verification commands and actual results

Commands run from repository root; Gradle invocations use `-Pandroid.newDsl=false --no-daemon` in this environment.

| Command | Result |
| --- | --- |
| `./gradlew :app:testDebugUnitTest -Pandroid.newDsl=false --no-daemon` | **Partial/blocker:** 251 tests completed, 2 failed at Robolectric `MavenArtifactFetcher` initialization with `SSLHandshakeException` / `PKIX path building failed` (`AppDatabaseTest`, `RoomScanRepositoryTest`). The other 249 tests passed. |
| `./gradlew :app:assembleDebug :app:assembleRelease -Pandroid.newDsl=false --no-daemon` | **PASS**, both variants built. Existing AGP/Kotlin deprecated API warnings remain. |
| `./gradlew :app:compileDebugAndroidTestKotlin -Pandroid.newDsl=false --no-daemon` | **PASS**, instrumentation test source compiles. |
| `./gradlew :app:dependencies --configuration releaseRuntimeClasspath --quiet -Pandroid.newDsl=false --no-daemon` with focused SDK-name search | **PASS**, no network/cloud/analytics/ads matches. |
| Parse merged release manifest; `aapt dump badging` release APK | **PASS** for declared permissions, `allowBackup=false`, exported component audit, and non-debuggable output. |
| `adb devices -l` | Could not start ADB daemon in this sandbox (`could not install *smartsocket* listener: Operation not permitted`). No connected device was available; no device test is claimed. |
| `./gradlew :app:connectedDebugAndroidTest -Pandroid.newDsl=false --no-daemon` | Previously attempted on this unchanged source state; instrumentation APK packaging passed, then task failed with `DeviceException: No connected devices`. |

## Prioritized recommendations

### P0

1. Make the permission status path safe in a normal app process; do not call inaccessible PackageManager allow-list queries from UI startup. Reproduce and verify launch on Android 16 with READ_SMS ungranted. Preserve Android's hard-restriction behavior; do not bypass it.

### P1

2. Define full-rescan reconciliation semantics and remove only stale **local offer metadata** whose source IDs are absent from a successful complete Inbox scan; never touch the Android SMS database. Ensure cancellation cannot trigger reconciliation.
3. Require expiry context before converting relative words to an expiration; add negative tests for promotion start times, delivery schedules, and unrelated dates.
4. Keep date precision/confidence and classification-vs-extraction confidence semantics consistent through metadata, Room, and UI.
5. Move large-list filtering/sorting off the main dispatcher and consider Paging/DAO queries if realistic offer counts grow; benchmark actual Room/provider paths with a representative large corpus.
6. Add Android/Robolectric permission flow tests and a launch smoke test with permission denied/restricted; test ContentResolver projection, cursor close, and cancellation on device/emulator where available.

### P2

7. Add migration fixture for a version-3 database and enable Room schema export/history to make future migrations reviewable.
8. Replace/remove disconnected production scaffolding and unused write APIs; keep one clearly owned scan persistence path.
9. Validate extraction against a privacy-reviewed, synthetic-plus-redacted corpus; report precision/recall per merchant/value/code/date field instead of treating rule weights as calibrated confidence.
10. Add navigation/permission/dashboard/settings Compose behavior tests and a Hilt app-start test. Keep instrumentation execution in CI with an emulator.
11. Minimize SMS projection to only fields actually needed (ADDRESS is currently read into `SmsMessage` but not used by extraction/persistence), subject to the Inbox API requirements.

## Recommended next development phase

**Stabilize permission-gated startup first:** remove the inaccessible allow-list call from the regular app path without changing permission policy, add a regression test for first launch with READ_SMS denied, and verify the app opens on Android 16. Then take the user's on-device crash report if available to confirm the exact stack trace. After startup is stable, address full-rescan stale metadata and expiration-context correctness, then add Room/provider and navigation permission tests.
