# CODEF bank transaction mapping gate

The current backend fixture proves date, time, withdrawal, deposit, balance, and description mappings. It does not contain a provider-stable transaction identifier or a verified pagination cursor.

Until an authorized CODEF response sample and current provider specification prove a stable identifier, the connector must emit `identityQuality=AMBIGUOUS` and finish the job with `DATA_IDENTITY_AMBIGUOUS` instead of publishing an importable chunk. A hash of date, amount, balance, and description is not accepted as proof of identity.
