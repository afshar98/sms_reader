# Offline SMS Discount Organizer Release Checklist

Audit date: 2026-11-22

## Release decision

**Not ready for signed distribution yet.** The release APK builds, but Gradle produces an unsigned APK, connected instrumentation tests could not run without an attached device, and two Room/Robolectric test classes could not initialize because Robolectric's Maven artifact fetch failed TLS certificate validation. `READ_SMS` is hard restricted on Android and may be unavailable to this sideloaded app unless the device's installer/OS has allow-listed it. Do not bypass that restriction or make the app the default SMS handler.

## Security and privacy audit

| Check | Result | Evidence / notes |
| --- | --- | --- |
| Release merged manifest inspected | PASS | `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml` |
| `INTERNET` permission absent | PASS | No match in the merged release manifest. |
| `SEND_SMS`, `RECEIVE_SMS`, `WRITE_SMS` absent | PASS | No match in the merged release manifest. `READ_SMS` is the only SMS permission. |
| Other unwanted permissions absent | PASS | No `READ_CONTACTS`, `READ_CALL_LOG`, or storage permissions. AndroidX adds its own signature-protected `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; it is not a data-access permission. |
| Network, cloud, analytics, telemetry, and advertising SDKs | PASS | Reviewed declared and resolved `releaseRuntimeClasspath`; no network client, cloud, analytics, telemetry, or advertising SDK matched. No application network calls were found. |
| SMS access is read-only | PASS | `AndroidSmsDataSource` queries `Telephony.Sms.Inbox.CONTENT_URI` through `ContentResolver`; no SMS insert, update, send, or delete operation was found. Local Room `DELETE` statements apply only to app-owned tables. |
| Raw SMS body persistence | PASS | The Room `OfferEntity` has no body or address field. The Inbox is streamed for processing; only offer metadata, source SMS ID, and timestamps are persisted. |
| Sensitive logging | PASS | No logging calls (`Log`, `Timber`, `println`, `System.out`) or crash/analytics logging SDK use was found in application source. |
| Backup configuration | PASS | Merged release manifest sets `android:allowBackup="false"`. |
| Exported components | PASS WITH NOTES | `MainActivity` is exported for the `MAIN`/`LAUNCHER` entry point only. AndroidX `ProfileInstallReceiver` is exported with `android.permission.DUMP`; AndroidX Startup provider and Room invalidation service are not exported. No SMS/default-handler component or intent filter exists. |
| Release debuggable flag | PASS | Release APK `aapt dump badging` output contains no `application-debuggable` flag; the merged release manifest also has no debuggable attribute. |
| Default SMS app behavior | PASS | No SMS role/default-app intent filters or SMS delivery components are present. |

### Local data retention

- Room stores extracted offer details (including merchant/title/coupon metadata), source SMS ID, source timestamp, and extraction timestamp; scan state stores a cursor and successful-scan time; preferences store theme values.
- Data has no automatic expiry. It remains in app-private storage until the user clears local app data or Android removes the app's data. A full rescan updates matching source SMS IDs but does not remove stored offers whose source SMS is no longer in Inbox.
- The Settings clear action transactionally clears offers, scan state, and Room preferences. The separate `sms_permission_state` SharedPreferences file retains a boolean recording whether a permission request was attempted; it contains no SMS data and is removed by Android's app-data clear/uninstall operation.
- The Room database is not application-encrypted. Its metadata relies on Android's app-private storage protections; `allowBackup=false` disables Android's standard app backup path, though device-to-device transfer behavior can vary by OS/OEM. Treat device compromise/root access as outside this application's storage protection.

## Performance and cancellation

| Check | Result | Evidence / notes |
| --- | --- | --- |
| Inbox work off the main thread | PASS BY CODE REVIEW | `AndroidSmsDataSource` uses `flowOn(Dispatchers.IO)` for `ContentResolver` and cursor operations; `ScanSmsUseCase` processes on `Dispatchers.Default`; Room writes are suspend/transactional. |
| Bounded Inbox processing | PASS | The data source emits rows as a Flow and closes its Cursor with `use`; scan offers are persisted in batches of 50. No full Inbox list is materialized by the pipeline. |
| Large synthetic scan | PASS | `largeSyntheticInboxStreamsInBoundedCheckpoints`: 10,000 generated rows, 201 checkpoint/final commits, completed in 0.202 seconds in the local JVM test run using a no-offer extractor and in-memory repository. This is not device performance data. |
| Cancellation safety | PASS IN UNIT TESTS | Integration test cancels after the first 50-row checkpoint, verifies the cursor remains safe, and confirms progress stops. |
| Offer list query order | PASS BY SQLITE QUERY PLAN; ROOM TEST BLOCKED | Added composite index `sourceDateMillis, sourceSmsId` and migration `3 -> 4`, matching the DAO sort. SQLite `EXPLAIN QUERY PLAN` on the matching table/index/query reports `SCAN offers USING INDEX index_offers_sourceDateMillis_sourceSmsId` with no temporary sort. Room-specific query-plan test is present but its Robolectric test class could not initialize in this environment. |
| Large saved-offer list memory | LIMITATION | `OfferDao.observeAll()` returns a list of all saved offer metadata. Inbox scan memory is bounded, but UI memory grows with the number of saved offers. This is suitable for the current personal-use corpus; consider Paging if that corpus grows substantially. |

## Verification record

Commands run from the repository root (using the configured Android SDK/JDK):

| Command / check | Result |
| --- | --- |
| `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin -Pandroid.newDsl=false --no-daemon` | Android test Kotlin compilation passed. Unit test task ran 251 tests: 249 passed; `AppDatabaseTest` and `RoomScanRepositoryTest` failed during Robolectric class initialization because the Maven artifact fetch ended in `SSLHandshakeException` / `PKIX path building failed`. Their test assertions did not run. |
| `./gradlew :app:testDebugUnitTest --tests 'com.example.smsdiscountorganizer.domain.scan.ScanSmsPipelineIntegrationTest.largeSyntheticInboxStreamsInBoundedCheckpoints' -Pandroid.newDsl=false --no-daemon` | PASS. |
| `./gradlew :app:assembleRelease -Pandroid.newDsl=false --no-daemon` | PASS. Release APK was produced at `app/build/outputs/apk/release/app-release-unsigned.apk` (about 45.5 MiB). Release lint vital passed. |
| `./gradlew :app:assembleRelease :app:connectedDebugAndroidTest -Pandroid.newDsl=false --no-daemon` | Release build and Android test APK packaging passed. Connected test execution was blocked with `DeviceException: No connected devices`; no device testing is claimed. |
| `./gradlew :app:dependencies --configuration releaseRuntimeClasspath --quiet -Pandroid.newDsl=false --no-daemon` plus focused search for network/analytics/ads SDK names | PASS; no matches. |
| `aapt dump badging` and `aapt dump xmltree` on release APK | PASS for application ID and non-debuggable release; confirms `READ_SMS`, backup disabled, launcher activity, and the AndroidX `DUMP`-protected receiver. |
| `apksigner verify --verbose --print-certs app-release-unsigned.apk` | Expectedly does not verify: the generated release APK is unsigned. |
| SQLite `EXPLAIN QUERY PLAN` using the Room offer schema, composite index, and DAO order expression | PASS; index scan, no temporary B-tree sort. |
| `adb devices -l` | No attached emulator/device. |

The build emits existing AGP/Kotlin DSL and `hiltViewModel` deprecation warnings. They did not fail the release build and were not changed as part of this audit.

## Signing and installation

The checked-in Gradle configuration has no release signing config, so `assembleRelease` produces an **unsigned** APK. Do not distribute or install that unsigned artifact. Create and protect a release keystore outside the repository; keep its passwords and backups in an approved secret store, and never commit the keystore or passwords.

Example local signing flow (the `keytool` command prompts for passwords):

```sh
keytool -genkeypair -keystore "$HOME/.android/sms-discount-organizer-release.jks" \
  -alias sms-discount-organizer -keyalg RSA -keysize 3072 -validity 10000

"$ANDROID_HOME/build-tools/36.0.0/apksigner" sign \
  --ks "$HOME/.android/sms-discount-organizer-release.jks" \
  --ks-key-alias sms-discount-organizer \
  --out app/build/outputs/apk/release/app-release.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk

"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk

adb devices
adb install -r app/build/outputs/apk/release/app-release.apk
```

Updates must use the same signing key as the installed build. Uninstalling first is only appropriate when losing that app's local data is acceptable. After installation, inspect the app's permission status and request `READ_SMS` only through the in-app explanation and Android permission UI.

## `READ_SMS` limitations

`READ_SMS` is a hard-restricted Android permission. Declaring it and showing a runtime prompt do not guarantee that Android will grant it. Grantability depends on Android version, installer allow-listing, device/OEM policy, and distribution route. The app detects installer restriction and reports it without trying to bypass the platform. If the status is installer-restricted, Inbox scanning is unavailable on that installation; use a platform-approved installer/device configuration or ship without SMS scanning. The app must remain non-default and read-only.
