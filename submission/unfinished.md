# Unfinished / Known Limitations

## 1. Corpus transaction-count difference

The current implementation produces 256 normalized transactions from the
522-message corpus, while the supplied fixture expectation is 257.

This difference is intentionally disclosed rather than artificially changing
the output to match the expected number.

## 2. Transfer edge cases

Some transfer-related edge cases still require further investigation to make
every account-level reconciliation value match the supplied fixture exactly.

## 3. SQL legacy uniqueness

The legacy SQL store does not have a database-level uniqueness constraint.
MongoDB uses stable transaction IDs and upserts for idempotent document writes.

## 4. Parser coverage

Additional unseen-corpus testing would be useful to expand coverage for
different bank message formats and edge cases.

## 5. Further validation

More time would allow deeper validation against additional corpora and more
failure-injection tests for the migration path.