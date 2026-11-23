# Domain language

- **Merchant:** The business evidenced by a recognized brand alias, an explicit merchant label in an offer message, a registered sender mapping, or a user's correction.
- **Sender identity:** The SMS `address` value supplied to the offline processor as transient evidence. It is not merchant proof unless an exact local registry mapping recognizes it, and it is never persisted with an offer.
- **Merchant correction:** A user-supplied local override for an offer's displayed merchant. It takes precedence over automated extraction and survives rescans until cleared.
- **Offer category:** A stable taxonomy identifier with an optional parent and zero or more applicable leaf categories. Multiple supported categories may apply at once.
- **General category:** Explicit evidence that an offer applies broadly across products or purchases.
- **Unknown category:** No supported category evidence was found. It is distinct from an explicitly broad offer.
