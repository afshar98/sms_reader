# Merchant and Category Evaluation

## Audit findings

- `MerchantExtractor` previously recognized only explicit store/brand labels. It had no brand alias list, no sender mapping, and returned an arbitrary label string without canonicalizing known names.
- `SmsMessage.address` already carried the Inbox sender value, but the offer extractor did not use it. It is now passed transiently to merchant resolution and is not added to offer storage.
- Generic sender IDs and numeric short codes had no evidence policy. A sender is now useful only when it exactly matches a registered brand mapping; generic gateway labels and unmapped short codes do not identify merchants.
- `ProductCategoryExtractor` used substring matching and `singleOrNull()`. It could match a word inside a larger term, and returned no category when multiple categories applied. The previous model also had no hierarchy and did not distinguish a broad offer from a category with insufficient evidence.
- No manual merchant correction path existed. Corrections are now editable on offer details, stored as derived local offer metadata, and retained when a later scan upserts the same offer.

## Taxonomy and category rules

Category IDs are stable lowercase strings stored independently of enum names or display labels. Parent relationships are metadata; offer metadata can contain more than one applicable category.

| Stable ID | Parent | Meaning / evidence |
| --- | --- | --- |
| `general` | — | Explicit broad terms such as «همه محصولات» or «همه کالاها» when no more specific category matches |
| `shopping` | `general` | Explicit broad shopping-center terms |
| `apparel` | `shopping` | Clothing, shoes, bags |
| `electronics` | `shopping` | Phones, computers, electronics |
| `beauty` | `shopping` | Cosmetics, hygiene, fragrance |
| `home` | `shopping` | Home appliances, furniture, decor |
| `food_and_drink` | `general` | Explicit broad food-and-drink terms |
| `grocery` | `food_and_drink` | Grocery, produce, supermarket terms |
| `food` | `food_and_drink` | Restaurants, cafes, fast food |
| `travel` | `general` | Hotels, trips, flights |
| `entertainment` | `general` | Cinema, concerts, theater |
| `unknown` | — | No supported category evidence |

Conflict rules:

1. Return all matching sibling categories; do not select one by rule order.
2. If a parent cue and one of its descendants both match, keep the more specific descendant and remove the parent.
3. Specific category evidence overrides a broad `general` cue.
4. `general` requires an explicit broad-scope phrase. `unknown` means the message supplied no supported category clue.
5. Matching uses normalized whole terms, including Persian zero-width non-joiners, to avoid partial-word matches.

The existing `ProductCategory` field remains as a compatibility projection for older callers and database rows. The new `applicableCategories` list is authoritative for multi-category UI and persistence.

## Merchant registry

The built-in local registry currently maps these names and aliases:

| Canonical name | Persian / English aliases | Sender aliases |
| --- | --- | --- |
| دیجی‌کالا | دیجی کالا, Digikala, Digi Kala, digikala.com | `DIGIKALA` |
| اسنپ‌فود | اسنپ فود, SnappFood | `SNAPPFOOD` |
| اسنپ‌مارکت | اسنپ مارکت, SnappMarket | `SNAPPMARKET` |
| تپسی | Tapsi | `TAPSI` |
| ترب | Torob | `TOROB` |
| بانی‌مد | بانی مد, BaniMode | `BANIMODE` |
| خانومی | Khanoumi, Khanomi | `KANOOMI` |
| اُکالا | اکالا, Okala | `OKALA` |
| دیجی‌استایل | دیجی استایل, DigiStyle | `DIGISTYLE` |
| مدیسه | Modiseh | `MODISEH` |

Evidence precedence is: user correction, explicit merchant label, one unambiguous known body alias, then one exact registered sender alias. If the body names multiple registered merchants, automatic resolution abstains. Generic gateway labels such as `SMS`, `INFO`, and `NOREPLY` are not merchant evidence. Numeric sender IDs have no default mapping. An explicit merchant label can identify a local business not present in the built-in alias list.

Manual corrections are stored separately from the automatic merchant result. The displayed/filterable name uses the correction while it exists; rescans preserve it, and clearing it returns to the automatic result. SMS address and body are not persisted by these changes.

## Synthetic evaluation

The fixture is deterministic and generated from ten merchant scenarios, ten category scenarios, two Persian offer-message frames, and twenty ambiguity/conflict cases. It contains **220 synthetic Persian SMS cases**; none came from a device Inbox.

Exact merchant match compares the canonical name (or expected abstention) with the returned name. Categories use multi-label membership. The confusion matrix below is one-vs-rest for each category: true positive, false positive, false negative, true negative.

| Metric | Result |
| --- | ---: |
| Merchant exact-match accuracy | 1.000 (220/220) |
| Category micro precision | 1.000 |
| Category micro recall | 1.000 |
| Category micro F1 | 1.000 |
| Supported-label macro F1 | 1.000 |

| Category ID | TP | FP | FN | TN |
| --- | ---: | ---: | ---: | ---: |
| `general` | 20 | 0 | 0 | 200 |
| `shopping` | 1 | 0 | 0 | 219 |
| `apparel` | 24 | 0 | 0 | 196 |
| `electronics` | 21 | 0 | 0 | 199 |
| `beauty` | 21 | 0 | 0 | 199 |
| `home` | 21 | 0 | 0 | 199 |
| `food_and_drink` | 1 | 0 | 0 | 219 |
| `grocery` | 21 | 0 | 0 | 199 |
| `food` | 21 | 0 | 0 | 199 |
| `travel` | 21 | 0 | 0 | 199 |
| `entertainment` | 20 | 0 | 0 | 200 |
| `unknown` | 30 | 0 | 0 | 190 |

These scores measure agreement with this synthetic fixture only. Its repeated templates and finite vocabulary make it unsuitable as an estimate of real-world accuracy or recall.

## Common failure patterns and limitations

- Offers sent only from an unmapped numeric short code remain merchant-unknown, even if the sender is familiar to a user. This is an intentional precision choice; users can correct the merchant on the offer details screen.
- A message mentioning two registered brands is ambiguous and returns no automatic merchant. Co-branded offers may therefore need a manual correction.
- A misspelled, stylized, or newly launched brand outside the local alias list is not inferred unless the SMS contains an explicit merchant label or its sender is registered.
- Category vocabulary is finite. A new product term remains `unknown` until supported by a rule; broad wording alone does not guess a leaf category.
- `ترب` is a short Persian token and may occur outside the brand context. Whole-token matching reduces substring matches, but a real corpus is still needed to measure this alias's false-positive rate.
- Synthetic evaluation does not cover every sender format, message style, negation, or colloquial spelling found on devices.
- The correction card was compiled but not interaction-tested: the generic Compose test host exposed no hierarchy, and `MainActivity` already owns its composition. The database correction behavior is covered by device-side instrumentation tests.

## Verification

- Engine unit tests: `:app:testDebugUnitTest --tests 'com.example.smsdiscountorganizer.engine.*'` — passed, including the 220-case evaluation.
- Device-side Room tests: `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.smsdiscountorganizer.data.local.MerchantCategoryPersistenceTest` — passed on Samsung SM-S711B / Android 16 (2 tests). This verifies the version 4→5 category migration, category round-trip, and correction retention across rescan upserts.
- Debug build and release manifest merge: `:app:assembleDebug :app:processReleaseManifest` — passed.
- Full unit suite: two Robolectric database test classes could not initialize because Robolectric could not fetch `org.robolectric:android-all-instrumented:15-robolectric-14663667-2-i7`; the host Java trust store rejected the artifact TLS certificate with `PKIX path building failed`. No network or trust bypass was added.
