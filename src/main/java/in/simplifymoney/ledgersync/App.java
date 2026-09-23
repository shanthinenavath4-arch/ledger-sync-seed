package in.simplifymoney.ledgersync;

import in.simplifymoney.ledgersync.ingest.IngestService;
import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.report.Reports;
import in.simplifymoney.ledgersync.store.Backfill;
import in.simplifymoney.ledgersync.store.ConsistencyChecker;
import in.simplifymoney.ledgersync.store.MongoDocumentStore;
import in.simplifymoney.ledgersync.store.SqlLedgerStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class App {

    private static final Path DB =
            Path.of("data", "ledger");

    private static final Path MIGRATIONS =
            Path.of("db", "migration");

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.err.println(
                    "usage: migrate | ingest <corpus.jsonl> | backfill | check | report <out-dir>"
            );
            System.exit(2);
        }

        Files.createDirectories(DB.getParent());

        switch (args[0]) {

            case "migrate" -> {

                try (SqlLedgerStore store =
                             new SqlLedgerStore(DB)) {

                    store.migrate(MIGRATIONS);

                    System.out.println(
                            "ledger rows: " + store.count()
                    );
                }
            }

            case "ingest" -> {

                if (args.length < 2) {
                    throw new IllegalArgumentException(
                            "ingest needs a corpus"
                    );
                }

                try (SqlLedgerStore store =
                             new SqlLedgerStore(DB)) {

                    store.migrate(MIGRATIONS);

                    var stats =
                            new IngestService(
                                    new Parsers(),
                                    store
                            ).ingestFile(
                                    Path.of(args[1])
                            );

                    System.out.println(stats);

                    System.out.println(
                            "ledger rows: " + store.count()
                    );
                }
            }

            case "backfill" -> {

                try (
                        SqlLedgerStore store =
                                new SqlLedgerStore(DB);

                        MongoDocumentStore documents =
                                new MongoDocumentStore()
                ) {

                    store.migrate(MIGRATIONS);

                    var result =
                            new Backfill(
                                    store,
                                    documents
                            ).run();

                    System.out.println(
                            "read: " + result.read()
                    );

                    System.out.println(
                            "written: " + result.written()
                    );

                    System.out.println(
                            "skipped: " + result.skipped()
                    );
                }
            }

            case "check" -> {

                try (
                        SqlLedgerStore store =
                                new SqlLedgerStore(DB);

                        MongoDocumentStore documents =
                                new MongoDocumentStore()
                ) {

                    store.migrate(MIGRATIONS);

                    var divergences =
                            new ConsistencyChecker(
                                    store,
                                    documents
                            ).check();

                    System.out.println(
                            "divergences: "
                                    + divergences.size()
                    );

                    for (var d : divergences) {

                        System.out.println(
                                d.what()
                                        + " | SQL="
                                        + d.inSql()
                                        + " | DOCUMENTS="
                                        + d.inDocuments()
                        );
                    }
                }
            }

            case "report" -> {

                if (args.length < 2) {
                    throw new IllegalArgumentException(
                            "report needs a directory"
                    );
                }

                Path out =
                        Path.of(args[1]);

                Files.createDirectories(out);

                try (SqlLedgerStore store =
                             new SqlLedgerStore(DB)) {

                    // Ensure the database schema exists.
                    store.migrate(MIGRATIONS);

                    // Reports must contain corpus transactions only,
                    // not the 15 legacy migration rows.
                    var ledger =
                            store.all()
                                    .stream()
                                    .filter(t ->
                                            t.sourceMessageIds()
                                                    .stream()
                                                    .noneMatch(
                                                            id -> id.startsWith(
                                                                    "m-legacy-"
                                                            )
                                                    )
                                    )
                                    .toList();

                    Files.writeString(
                            out.resolve("ledger.json"),
                            Json.writePretty(
                                    Reports.ledgerDocument(
                                            ledger
                                    )
                            )
                    );

                    Files.writeString(
                            out.resolve("summary.json"),
                            Json.writePretty(
                                    Reports.summary(
                                            ledger
                                    )
                            )
                    );

                    Map<String, Object> expected =
                            Json.parseObject(
                                    Files.readString(
                                            Path.of(
                                                    "fixtures",
                                                    "corpus-a-totals.json"
                                            )
                                    )
                            );

                    Files.writeString(
                            out.resolve(
                                    "reconciliation.json"
                            ),
                            Json.writePretty(
                                    Reports.reconciliation(
                                            ledger,
                                            expected
                                    )
                            )
                    );

                    System.out.println(
                            "wrote 3 files to " + out
                    );
                }
            }

            default -> {

                System.err.println(
                        "unknown command: " + args[0]
                );

                System.exit(2);
            }
        }
    }
}