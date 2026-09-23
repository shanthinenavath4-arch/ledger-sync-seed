const db = db.getSiblingDB("ledger_benchmark");

db.transactions.drop();

const docs = [];

const accounts = ["4821", "9075", "3310", "6244"];
const categories = ["SPEND", "INCOME", "MICRO", "TRANSFER"];

for (let i = 0; i < 100000; i++) {
    const account = accounts[i % accounts.length];
    const category = categories[i % categories.length];

    const day = (i % 28) + 1;
    const hour = i % 24;
    const minute = i % 60;

    const occurredAt =
        `2026-07-${String(day).padStart(2, "0")}T` +
        `${String(hour).padStart(2, "0")}:` +
        `${String(minute).padStart(2, "0")}:00+05:30`;

    docs.push({
        _id: `benchmark-${i}`,
        account_last4: account,
        occurred_at: occurredAt,
        direction: category === "INCOME" ? "CREDIT" : "DEBIT",
        amount: ((i % 5000) + 1).toFixed(2),
        category: category,
        merchant: `MERCHANT-${i % 100}`,
        source_message_ids: [`benchmark-message-${i}`]
    });

    if (docs.length === 1000) {
        db.transactions.insertMany(docs);
        docs.length = 0;
    }
}

if (docs.length > 0) {
    db.transactions.insertMany(docs);
}

db.transactions.createIndex({
    account_last4: 1,
    occurred_at: 1
});

db.transactions.createIndex({
    account_last4: 1,
    category: 1
});

db.transactions.createIndex({
    source_message_ids: 1
});

print("Benchmark documents:");
print(db.transactions.countDocuments());