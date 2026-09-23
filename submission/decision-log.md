# Decision Log

## 1. MongoDB instead of DynamoDB
MongoDB was selected because the assignment explicitly permits it and it was
straightforward to run locally using Docker Compose.

## 2. Stable transaction identity
A transaction identity based on account, occurred_at, direction, amount and
merchant was used to group multiple evidence messages for the same transaction.

## 3. Preserve all source message IDs
Message IDs identify uploaded evidence rather than necessarily unique
transactions, so all evidence IDs are retained in source_message_ids.

## 4. BigDecimal for money
BigDecimal with two decimal places was used instead of floating-point values
to preserve exact paisa-level amounts.

## 5. Use bank-stated transaction time
The bank's stated transaction time is used for occurred_at instead of the
message received/upload time.

## 6. Separate MICRO from normal SPEND
UPI debits of ₹100 or less are classified as MICRO. They remain individually
represented in the ledger but are rolled up in summary totals.

## 7. Exclude TRANSFER from spend and income
Transfers between the user's own accounts represent movement of money rather
than spending or income.

## 8. MongoDB upsert
MongoDB writes use stable IDs with upsert so repeated writes do not create
duplicate documents.

## 9. Aggregation for category totals
MongoDB aggregation is used for category totals so the database can perform
the grouping and summation directly.

## 10. Do not hide reconciliation differences
The corpus currently produces 256 normalized transactions while the supplied
fixture expectation is 257. I chose to disclose this difference rather than
artificially changing the output to make the numbers match.