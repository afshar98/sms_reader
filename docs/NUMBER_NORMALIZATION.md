# Number normalization and Persian display

## Internal values

`NumberNormalizer` is the shared engine utility for numeric text. It converts Persian (`۰۱۲۳۴۵۶۷۸۹`) and Arabic (`٠١٢٣٤٥٦٧٨٩`) digits to ASCII and parses numbers to `BigDecimal` before extraction or comparison. Existing `DigitNormalizer` remains as a compatibility wrapper and date parsers use it for calendar and relative-time input.

Separator handling is deliberate:

- Persian `٫` marks a decimal and Persian `٬` marks digit grouping.
- Comma, Arabic comma, and dot are grouping separators when they form valid groups of three digits (for example `150,000`). A single separator that does not form valid grouping is treated as a decimal separator (for example `12,5`).
- A mixed set of comma and dot separators is accepted only when it forms valid grouping followed by an unambiguous fractional part. Ambiguous and malformed inputs return no parsed value instead of a guessed conversion.
- Whitespace around separators is tolerated. Whitespace used as a grouping separator is accepted only with valid three-digit groups; malformed digit spacing is rejected.

The SMS source remains unchanged. Normalization happens on derived text and parsed values. Extracted amounts are stored as plain ASCII decimal strings; existing source SMS IDs and Room identifiers are unaffected. Percentage and monetary discount extraction, minimum purchase extraction, Jalali/Gregorian date parsing, relative-hour parsing, and numeric offer search use this canonical representation.

## Persian UI formatting

`PersianNumberFormatter` belongs to presentation code. It groups integer thousands with `٬`, uses `٫` for decimal values, renders digits in Persian, and renders percent as `٪`. Dashboard counts and scan progress, offer values, details, diagnostics counts, and formatted timestamps use Persian digits. No database values are localized.

Coupon codes remain identifiers: they are excluded from numeric search normalization, displayed with Unicode bidi isolation in RTL layouts, and copied from their exact stored string. Search normalizes numeric offer fields while comparing coupon codes as entered.

## Boundaries

The current offer schema has no separate maximum-discount field. This change normalizes maximum-cap amounts wherever they are represented as ordinary monetary values; adding a distinct maximum-discount field requires a separate extraction and Room schema phase.
