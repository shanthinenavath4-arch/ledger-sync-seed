package in.simplifymoney.ledgersync.ingest;

import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.store.LedgerStore;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class IngestService {

    private final Parsers parsers;
    private final LedgerStore store;

    public IngestService(Parsers parsers, LedgerStore store) {
        this.parsers = parsers;
        this.store = store;
    }

    public Stats ingestFile(Path corpus) throws IOException {

        List<RawMessage> messages = readCorpus(corpus);

        int skipped = 0;

        Map<TransactionKey, List<ParsedTxn>> grouped =
                new LinkedHashMap<>();

        for (RawMessage message : messages) {

            Optional<ParsedTxn> parsed = parsers.parse(message);

            if (parsed.isEmpty()) {
                skipped++;
                continue;
            }

            ParsedTxn txn = parsed.get();

            TransactionKey key = new TransactionKey(
                    txn.accountLast4(),
                    txn.occurredAt(),
                    txn.direction(),
                    txn.amount(),
                    normalizeMerchant(txn.merchant())
            );

            grouped
                    .computeIfAbsent(
                            key,
                            ignored -> new ArrayList<>()
                    )
                    .add(txn);
        }

        List<ParsedTxn> allTransactions =
                grouped.values()
                        .stream()
                        .map(list -> list.get(0))
                        .toList();

        for (List<ParsedTxn> transactions : grouped.values()) {

            ParsedTxn first = transactions.get(0);

            boolean transfer =
                    isTransfer(first, allTransactions);

            NormalizedTxn normalized =
                    toTransaction(
                            transactions,
                            transfer
                    );

            store.save(normalized);
        }

        return new Stats(
                messages.size(),
                grouped.size(),
                skipped
        );
    }

    public static List<RawMessage> readCorpus(Path corpus)
            throws IOException {

        List<RawMessage> out = new ArrayList<>();

        try (Stream<String> lines = Files.lines(corpus)) {

            for (String line : (Iterable<String>) lines
                    .filter(s -> !s.isBlank())::iterator) {

                Map<String, Object> object =
                        Json.parseObject(line);

                out.add(
                        new RawMessage(
                                (String) object.get("message_id"),
                                (String) object.get("channel"),
                                (String) object.get("sender"),
                                OffsetDateTime.parse(
                                        (String) object.get("received_at")
                                ),
                                (String) object.get("device_id"),
                                (String) object.get("body")
                        )
                );
            }
        }

        return out;
    }

    private NormalizedTxn toTransaction(
            List<ParsedTxn> transactions,
            boolean transfer) {

        ParsedTxn first = transactions.get(0);

        Category category;

        if (transfer) {

            category = Category.TRANSFER;

        } else if (
                first.direction() == Direction.DEBIT
                        && first.amount().compareTo(
                                BigDecimal.valueOf(100)
                        ) <= 0) {

            category = Category.MICRO;

        } else if (
                first.direction() == Direction.DEBIT) {

            category = Category.SPEND;

        } else {

            category = Category.INCOME;
        }

        List<String> sourceMessageIds =
                transactions
                        .stream()
                        .map(ParsedTxn::sourceMessageId)
                        .distinct()
                        .sorted()
                        .toList();

        return new NormalizedTxn(
                first.accountLast4(),
                first.occurredAt(),
                first.direction(),
                first.amount(),
                category,
                first.merchant(),
                sourceMessageIds
        );
    }

    private boolean isTransfer(
            ParsedTxn txn,
            List<ParsedTxn> allTransactions) {

        String merchant =
                normalizeMerchant(txn.merchant());

        boolean transferLike =
                merchant.contains("IMPS/P2A")
                        || merchant.contains("NEFT INWARD")
                        || merchant.contains("NEFT INWARD SELF");

        if (!transferLike) {
            return false;
        }

        for (ParsedTxn other : allTransactions) {

            if (txn.accountLast4()
                    .equals(other.accountLast4())) {
                continue;
            }

            if (txn.direction()
                    == other.direction()) {
                continue;
            }

            if (txn.amount()
                    .compareTo(other.amount()) != 0) {
                continue;
            }

            String otherMerchant =
                    normalizeMerchant(other.merchant());

            boolean otherTransferLike =
                    otherMerchant.contains("IMPS/P2A")
                            || otherMerchant.contains("NEFT INWARD")
                            || otherMerchant.contains("NEFT INWARD SELF");

            if (!otherTransferLike) {
                continue;
            }

            long seconds =
                    Math.abs(
                            Duration.between(
                                    txn.occurredAt(),
                                    other.occurredAt()
                            ).getSeconds()
                    );

            if (seconds <= 5 * 60) {
                return true;
            }
        }

        return false;
    }

    private static String normalizeMerchant(
            String merchant) {

        if (merchant == null) {
            return "";
        }

        return merchant
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase();
    }

    private record TransactionKey(
            String accountLast4,
            OffsetDateTime occurredAt,
            Direction direction,
            BigDecimal amount,
            String merchant) {
    }

    public record Stats(
            int messagesRead,
            int transactionsWritten,
            int messagesSkipped) {
    }
}