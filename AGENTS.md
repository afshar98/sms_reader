# Repository Engineering Rules

These rules apply to all work in this repository. Preserve the app's offline, read-only handling of SMS in every phase.

## Product and architecture

- Build SMS Discount Organizer as a personal-use Android application that processes and stores all data on-device.
- Use Kotlin, Jetpack Compose, Material 3, MVVM with lightweight Clean Architecture, Hilt, Room, Kotlin Coroutines and Flow, and Gradle Kotlin DSL.
- Keep the processing engine independent of Android APIs. Put Android-specific access, including SMS access, behind platform-facing boundaries.
- Set `minSdk` to 29. Use mutually compatible stable Android tooling and dependency versions; check the project's existing version catalog and build configuration before changing versions.
- Keep changes within the current phase and the requested scope.

## SMS privacy and security

- The final merged manifest must contain no `INTERNET`, `SEND_SMS`, `RECEIVE_SMS`, `WRITE_SMS`, `READ_CONTACTS`, or `READ_CALL_LOG` permission. `READ_SMS` is the only permitted SMS permission.
- Read Inbox messages only through Android `ContentResolver` APIs, with `READ_SMS` permission as needed.
- Treat SMS access as strictly read-only. Never send, edit, delete, or otherwise modify SMS, and never implement default-SMS-app behavior.
- Keep all processing and storage on-device. Do not add network calls, cloud services, analytics, telemetry, advertising SDKs, or remote AI integrations.
- Never log SMS content or other sensitive message data. Do not persist full SMS bodies; store only the minimum derived data needed by the app.
- Set `android:allowBackup="false"` for the application.
- When a requested change conflicts with these protections, stop the affected work and report the blocker instead of weakening or bypassing a protection.

## Engineering workflow

- Inspect relevant existing code, configuration, and uncommitted changes before editing. Preserve unrelated work and avoid overwriting changes outside the requested scope.
- Add or update tests for business logic in each implementation phase.
- After each phase, run the relevant tests and build. Inspect the final merged manifest for the required permissions, backup setting, and SMS app behavior.
- Report the verification commands and their actual results. Clearly report any check that could not run and its blocker; never claim unrun checks passed.
- Commit only after successful verification, using a Conventional Commit message.
