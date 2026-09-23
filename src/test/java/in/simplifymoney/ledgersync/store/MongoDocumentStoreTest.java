package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MongoDocumentStoreTest {

    @Test
    void savesAndReadsTransactionFromMongo() {

        try (MongoDocumentStore store = new MongoDocumentStore()) {

            NormalizedTxn txn = new NormalizedTxn(
                    "9999",
                    OffsetDateTime.of(
                            2026,
                            7,
                            23,
                            18,
                            41,
                            0,
                            0,
                            ZoneOffset.ofHoursMinutes(5, 30)
                    ),
                    Direction.DEBIT,
                    new BigDecimal("5.00"),
                    Category.MICRO,
                    "UPI/BARBER",
                    List.of("mongo-test-9999")
            );

            store.save(txn);

            var byMessage =
                    store.byMessageId("mongo-test-9999");

            assertTrue(byMessage.isPresent());

            assertEquals(
                    "9999",
                    byMessage.get().accountLast4()
            );

            assertEquals(
                    new BigDecimal("5.00"),
                    byMessage.get().amount()
            );

            assertEquals(
                    Category.MICRO,
                    byMessage.get().category()
            );

            var month =
                    store.forAccountMonth(
                            "9999",
                            YearMonth.of(2026, 7)
                    );

            assertEquals(1, month.size());

            assertEquals(
                    "mongo-test-9999",
                    month.get(0).sourceMessageIds().get(0)
            );

            var totals =
                    store.categoryTotals("9999");

            assertEquals(
                    new BigDecimal("5.00"),
                    totals.get(Category.MICRO)
            );
        }
    }
}