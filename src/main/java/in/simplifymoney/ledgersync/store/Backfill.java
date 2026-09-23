package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.NormalizedTxn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Moves everything already in the SQL store into the document store.
 *
 * The SQL store can contain duplicate legacy rows, so transactions are
 * grouped by their natural transaction identity before being written.
 *
 * The target store uses an idempotent save, so running this more than once
 * produces the same document state.
 */
public final class Backfill {

    private final SqlLedgerStore source;
    private final DocumentStore target;

    public Backfill(SqlLedgerStore source, DocumentStore target) {
        this.source = source;
        this.target = target;
    }

    public Result run() {

        List<NormalizedTxn> rows = source.all();

        Map<TransactionKey, NormalizedTxn> unique =
                new LinkedHashMap<>();

        long skipped = 0;

        for (NormalizedTxn txn : rows) {

            TransactionKey key = new TransactionKey(
                    txn.accountLast4(),
                    txn.occurredAt().toString(),
                    txn.direction().name(),
                    txn.amount().toPlainString(),
                    txn.merchant()
            );

            NormalizedTxn existing = unique.get(key);

            if (existing == null) {
                unique.put(key, txn);
            } else {
                unique.put(
                        key,
                        merge(existing, txn)
                );
                skipped++;
            }
        }

        long written = 0;

        for (NormalizedTxn txn : unique.values()) {
            target.save(txn);
            written++;
        }

        return new Result(
                rows.size(),
                written,
                skipped
        );
    }

    private static NormalizedTxn merge(
            NormalizedTxn first,
            NormalizedTxn second) {

        List<String> messageIds =
                new ArrayList<>(first.sourceMessageIds());

        for (String id : second.sourceMessageIds()) {
            if (!messageIds.contains(id)) {
                messageIds.add(id);
            }
        }

        messageIds.sort(String::compareTo);

        return new NormalizedTxn(
                first.accountLast4(),
                first.occurredAt(),
                first.direction(),
                first.amount(),
                first.category(),
                first.merchant(),
                messageIds
        );
    }

    private record TransactionKey(
            String accountLast4,
            String occurredAt,
            String direction,
            String amount,
            String merchant) {
    }

    public record Result(
            long read,
            long written,
            long skipped) {
    }
}