package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.NormalizedTxn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConsistencyChecker {

    private final SqlLedgerStore sql;
    private final DocumentStore documents;

    public ConsistencyChecker(
            SqlLedgerStore sql,
            DocumentStore documents) {

        this.sql = sql;
        this.documents = documents;
    }

    public List<Divergence> check() {

        List<Divergence> divergences =
                new ArrayList<>();

        /*
         * SQL contains intentional legacy duplicates.
         * Group them by natural transaction identity first,
         * then merge their source message ids.
         */
        Map<String, NormalizedTxn> expected =
                new HashMap<>();

        for (NormalizedTxn txn : sql.all()) {

            String key = naturalKey(txn);

            NormalizedTxn existing =
                    expected.get(key);

            if (existing == null) {
                expected.put(key, txn);
            } else {
                expected.put(
                        key,
                        merge(existing, txn)
                );
            }
        }

        /*
         * Compare every unique SQL transaction with Mongo.
         */
        for (Map.Entry<String, NormalizedTxn> entry
                : expected.entrySet()) {

            String key = entry.getKey();
            NormalizedTxn sqlTxn = entry.getValue();

            NormalizedTxn documentTxn = null;

            /*
             * Try every expected message id.
             * This avoids depending on one particular
             * duplicate row being the first one.
             */
            for (String messageId :
                    sqlTxn.sourceMessageIds()) {

                documentTxn =
                        documents.byMessageId(messageId)
                                .orElse(null);

                if (documentTxn != null) {
                    break;
                }
            }

            if (documentTxn == null) {

                divergences.add(
                        new Divergence(
                                "missing transaction: " + key,
                                describe(sqlTxn),
                                "<missing>"
                        )
                );

                continue;
            }

            compare(
                    key,
                    sqlTxn,
                    documentTxn,
                    divergences
            );
        }

        return divergences;
    }

    private static NormalizedTxn merge(
            NormalizedTxn first,
            NormalizedTxn second) {

        Set<String> ids =
                new HashSet<>(
                        first.sourceMessageIds()
                );

        ids.addAll(
                second.sourceMessageIds()
        );

        List<String> mergedIds =
                new ArrayList<>(ids);

        mergedIds.sort(String::compareTo);

        return new NormalizedTxn(
                first.accountLast4(),
                first.occurredAt(),
                first.direction(),
                first.amount(),
                first.category(),
                first.merchant(),
                mergedIds
        );
    }

    private void compare(
            String key,
            NormalizedTxn sqlTxn,
            NormalizedTxn documentTxn,
            List<Divergence> divergences) {

        if (!sqlTxn.accountLast4()
                .equals(documentTxn.accountLast4())) {

            divergences.add(
                    new Divergence(
                            key + " account",
                            sqlTxn.accountLast4(),
                            documentTxn.accountLast4()
                    )
            );
        }

        if (!sqlTxn.occurredAt()
                .equals(documentTxn.occurredAt())) {

            divergences.add(
                    new Divergence(
                            key + " occurred_at",
                            sqlTxn.occurredAt().toString(),
                            documentTxn.occurredAt().toString()
                    )
            );
        }

        if (sqlTxn.direction()
                != documentTxn.direction()) {

            divergences.add(
                    new Divergence(
                            key + " direction",
                            sqlTxn.direction().name(),
                            documentTxn.direction().name()
                    )
            );
        }

        if (sqlTxn.amount()
                .compareTo(documentTxn.amount()) != 0) {

            divergences.add(
                    new Divergence(
                            key + " amount",
                            sqlTxn.amount().toPlainString(),
                            documentTxn.amount().toPlainString()
                    )
            );
        }

        if (sqlTxn.category()
                != documentTxn.category()) {

            divergences.add(
                    new Divergence(
                            key + " category",
                            sqlTxn.category().name(),
                            documentTxn.category().name()
                    )
            );
        }

        if (!sqlTxn.merchant()
                .equals(documentTxn.merchant())) {

            divergences.add(
                    new Divergence(
                            key + " merchant",
                            sqlTxn.merchant(),
                            documentTxn.merchant()
                    )
            );
        }

        Set<String> sqlIds =
                new HashSet<>(
                        sqlTxn.sourceMessageIds()
                );

        Set<String> documentIds =
                new HashSet<>(
                        documentTxn.sourceMessageIds()
                );

        if (!sqlIds.equals(documentIds)) {

            divergences.add(
                    new Divergence(
                            key + " source_message_ids",
                            sqlIds.toString(),
                            documentIds.toString()
                    )
            );
        }
    }

    private static String naturalKey(
            NormalizedTxn txn) {

        return txn.accountLast4()
                + "|"
                + txn.occurredAt()
                + "|"
                + txn.direction()
                + "|"
                + txn.amount().toPlainString()
                + "|"
                + txn.merchant();
    }

    private static String describe(
            NormalizedTxn txn) {

        return "account=" + txn.accountLast4()
                + ", occurred_at=" + txn.occurredAt()
                + ", direction=" + txn.direction()
                + ", amount="
                + txn.amount().toPlainString()
                + ", category=" + txn.category()
                + ", merchant=" + txn.merchant()
                + ", source_message_ids="
                + txn.sourceMessageIds();
    }

    public record Divergence(
            String what,
            String inSql,
            String inDocuments) {
    }
}