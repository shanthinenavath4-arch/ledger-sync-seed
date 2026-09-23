package in.simplifymoney.ledgersync.store;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;

import org.bson.Document;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MongoDocumentStore implements DocumentStore, AutoCloseable {

    private final MongoClient client;
    private final MongoCollection<Document> collection;

    public MongoDocumentStore() {
        this(
                System.getenv().getOrDefault(
                        "MONGODB_URI",
                        "mongodb://localhost:27017"
                ),
                "ledger"
        );
    }

    public MongoDocumentStore(String uri, String databaseName) {
        this.client = MongoClients.create(uri);

        MongoDatabase database = client.getDatabase(databaseName);
        this.collection = database.getCollection("transactions");

        createIndexes();
    }

    private void createIndexes() {

        collection.createIndex(
                Indexes.ascending(
                        "account_last4",
                        "occurred_at"
                )
        );

        collection.createIndex(
                Indexes.ascending(
                        "account_last4",
                        "category"
                )
        );

        collection.createIndex(
                Indexes.ascending(
                        "source_message_ids"
                )
        );
    }

    @Override
    public List<NormalizedTxn> forAccountMonth(
            String accountLast4,
            YearMonth month) {

        OffsetDateTime start =
                month.atDay(1)
                        .atStartOfDay()
                        .atOffset(
                                java.time.ZoneOffset.ofHoursMinutes(
                                        5,
                                        30
                                )
                        );

        OffsetDateTime end =
                month.plusMonths(1)
                        .atDay(1)
                        .atStartOfDay()
                        .atOffset(
                                java.time.ZoneOffset.ofHoursMinutes(
                                        5,
                                        30
                                )
                        );

        List<Document> documents =
                collection.find(
                                Filters.and(
                                        Filters.eq(
                                                "account_last4",
                                                accountLast4
                                        ),
                                        Filters.gte(
                                                "occurred_at",
                                                start.toString()
                                        ),
                                        Filters.lt(
                                                "occurred_at",
                                                end.toString()
                                        )
                                )
                        )
                        .sort(
                                Sorts.descending(
                                        "occurred_at"
                                )
                        )
                        .into(
                                new ArrayList<>()
                        );

        return documents.stream()
                .map(MongoDocumentStore::fromDocument)
                .toList();
    }

    @Override
    public Map<Category, BigDecimal> categoryTotals(
            String accountLast4) {

        Map<Category, BigDecimal> totals =
                new EnumMap<>(Category.class);

        for (Category category : Category.values()) {
            totals.put(
                    category,
                    BigDecimal.ZERO.setScale(2)
            );
        }

        List<Document> results =
                collection.aggregate(
                        List.of(
                                Aggregates.match(
                                        Filters.eq(
                                                "account_last4",
                                                accountLast4
                                        )
                                ),

                                Aggregates.group(


                                        "$category",

                                        Accumulators.sum(

                                                "total",

                                                new Document(
                                                "$toDecimal",

                                                
                                                "$amount"

                                                 )

                                                )

                                        )
                                        

                        )
                )
                .into(
                        new ArrayList<>()
                );

        for (Document document : results) {

            String categoryName =
                    document.getString("_id");

            Object total =
                    document.get("total");

            if (categoryName == null || total == null) {
                continue;
            }

            BigDecimal amount =
                    new BigDecimal(
                            total.toString()
                    ).setScale(2);

            totals.put(
                    Category.valueOf(categoryName),
                    amount
            );
        }

        return totals;
    }

    @Override
    public Optional<NormalizedTxn> byMessageId(
            String messageId) {

        Document document =
                collection.find(
                        Filters.eq(
                                "source_message_ids",
                                messageId
                        )
                )
                .first();

        if (document == null) {
            return Optional.empty();
        }

        return Optional.of(
                fromDocument(document)
        );
    }

    @Override
    public void save(NormalizedTxn txn) {

        String id =
                stableId(txn);

        Document document =
                new Document()
                        .append(
                                "_id",
                                id
                        )
                        .append(
                                "account_last4",
                                txn.accountLast4()
                        )
                        .append(
                                "occurred_at",
                                txn.occurredAt().toString()
                        )
                        .append(
                                "direction",
                                txn.direction().name()
                        )
                        .append(
                                "amount",
                                txn.amount()
                                        .setScale(2)
                                        .toPlainString()
                        )
                        .append(
                                "category",
                                txn.category().name()
                        )
                        .append(
                                "merchant",
                                txn.merchant()
                        )
                        .append(
                                "source_message_ids",
                                txn.sourceMessageIds()
                        );

        collection.replaceOne(
                Filters.eq(
                        "_id",
                        id
                ),
                document,
                new ReplaceOptions()
                        .upsert(true)
        );
    }

    private static String stableId(
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

    private static NormalizedTxn fromDocument(
            Document document) {

        String accountLast4 =
                document.getString(
                        "account_last4"
                );

        OffsetDateTime occurredAt =
                OffsetDateTime.parse(
                        document.getString(
                                "occurred_at"
                        )
                );

        Direction direction =
                Direction.valueOf(
                        document.getString(
                                "direction"
                        )
                );

        BigDecimal amount =
                new BigDecimal(
                        document.getString(
                                "amount"
                        )
                ).setScale(2);

        Category category =
                Category.valueOf(
                        document.getString(
                                "category"
                        )
                );

        String merchant =
                document.getString(
                        "merchant"
                );

        List<String> sourceMessageIds =
                document.getList(
                        "source_message_ids",
                        String.class
                );

        return new NormalizedTxn(
                accountLast4,
                occurredAt,
                direction,
                amount,
                category,
                merchant,
                sourceMessageIds
        );
    }

    @Override
    public void close() {
        client.close();
    }
}