# Sender Merchant Intelligence Benchmark

## Synthetic accuracy

`SenderMerchantProfileBuilderTest.benchmarkFixtureMeasuresAtLeastTwoHundredSyntheticMessages` evaluates 200 synthetic Persian Inbox messages across five sender IDs and five known merchants. Each sender has 32 messages naming its merchant and eight ambiguous messages with no merchant name. The benchmark evaluates each message against the profile resolved for its normalized sender.

| Measure | Result |
| --- | ---: |
| Synthetic messages | 200 |
| Exact merchant matches | 200 / 200 (100%) |
| Explicit current-message matches | 160 / 160 (100%) |
| Ambiguous messages resolved from sender history | 40 / 40 (100%) |
| Incorrect merchant assignments | 0 |
| Unknown/abstentions in this fixture | 0 |

These are controlled synthetic results, not an estimate of real Inbox accuracy. The fixture supplies repeated, consistent brand evidence for each sender. Separate tests cover weak evidence, multiple brands on a sender, shared-gateway detection, duplicate rows, generic wording, current-message precedence, and category extraction from the current message only.

## Resolution rules measured

- Sender IDs are normalized locally and persisted only as SHA-256 fingerprints.
- Evidence is a set of distinct merchant names per message, so repeated aliases in one SMS contribute one support.
- A clean sender profile resolves after at least three independent supporting messages (confidence `support / (support + 1)`, threshold `0.75`).
- Multiple supported merchant names are a conflict and resolve to `UNKNOWN` through a null merchant. A sender is flagged as a shared gateway when at least two names each have two or more supporting messages.
- Explicit current-message evidence is checked before sender history. Sender history supplies merchant identity only; categories continue to come from the current message.

## APK size

Sizes are the packaged APK byte counts from the local build output. The earlier debug APK was measured before this change; no earlier release APK measurement was available.

| Artifact | Bytes | Comparison |
| --- | ---: | ---: |
| Debug APK before change | 65,461,879 | Existing local artifact |
| Debug APK after change | 65,377,423 | 84,456 bytes smaller (about 0.13%) |
| Unsigned release APK after change | 47,860,249 | No prior release artifact for comparison |

The before/after debug comparison is a file-size observation, not a causal estimate: the earlier APK's exact build provenance was not recorded. No SDK, runtime model, network library, or cloud dependency was added for this feature.
