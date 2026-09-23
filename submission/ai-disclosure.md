# AI Disclosure

AI tools were used during development for:

- Understanding the existing Java and Gradle project structure.
- Debugging parser regular expressions.
- Reviewing MongoDB document and index design.
- Generating initial implementation ideas.
- Debugging test failures.
- Reviewing documentation structure.

AI-generated suggestions were treated as drafts and were tested against the
actual code, corpus, and test suite before being used.

## Concrete Example Where AI Was Wrong

During investigation of the transaction grouping issue, an AI suggestion
included the available balance as part of the transaction identity.

That approach was incorrect because the available balance is not part of the
transaction itself. Using it as an identity field caused the same transaction
to be split into multiple transactions when different evidence messages had
different balances.

The implemented approach keeps the available balance separate from the
transaction identity and uses the transaction's account, occurred_at,
direction, amount and normalized merchant for grouping.

This was checked against the corpus and the resulting transaction grouping.