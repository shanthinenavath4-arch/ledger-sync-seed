# ledger-sync

Simplify Money — Software Engineer/Intern (Backend, Java) take-home assignment.

This project parses bank SMS/email messages into a normalized transaction ledger,
generates account summaries and reconciliation output, fixes the water-can amount
parsing incident, and moves ledger storage from SQL to MongoDB.

---

## 1. What the service does

The service takes bank SMS and email messages from:

```text
fixtures/corpus-a.jsonl