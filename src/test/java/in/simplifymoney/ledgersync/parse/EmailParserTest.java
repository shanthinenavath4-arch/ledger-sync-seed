package in.simplifymoney.ledgersync.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class EmailParserTest {

    private final EmailParser parser = new EmailParser();

    @Test
    void parsesDebitEmail() {
        RawMessage message = new RawMessage(
                "m-test-debit",
                "email",
                "alerts@hdfcbank.net",
                OffsetDateTime.parse("2026-07-02T11:49:00+05:30"),
                "dev-test",
                """
                Date: Thu, 02 Jul 2026 11:04:00 +0530
                Subject: Transaction alert on your account

                Dear Customer,

                Your account ending 4821 has been debited with Rs.76.49.
                Merchant / Remarks: RELIANCE SMART
                Transaction reference: 7125305049

                This is a system generated email.
                """);

        var result = parser.parse(message);

        assertTrue(result.isPresent());

        ParsedTxn txn = result.get();

        assertEquals("4821", txn.accountLast4());
        assertEquals(Direction.DEBIT, txn.direction());
        assertEquals(new BigDecimal("76.49"), txn.amount());
        assertEquals("RELIANCE SMART", txn.merchant());
        assertEquals(
                OffsetDateTime.parse("2026-07-02T11:04:00+05:30"),
                txn.occurredAt());
        assertEquals("m-test-debit", txn.sourceMessageId());
    }

    @Test
    void parsesCreditEmail() {
        RawMessage message = new RawMessage(
                "m-test-credit",
                "email",
                "alerts@hdfcbank.net",
                OffsetDateTime.parse("2026-07-01T09:47:00+05:30"),
                "dev-test",
                """
                Date: Wed, 01 Jul 2026 09:02:00 +0530
                Subject: Transaction alert on your account

                Dear Customer,

                Your account ending 4821 has been credited with INR 45,000.
                Merchant / Remarks: SALARY CREDIT
                Transaction reference: 1597155421

                This is a system generated email.
                """);

        var result = parser.parse(message);

        assertTrue(result.isPresent());

        ParsedTxn txn = result.get();

        assertEquals("4821", txn.accountLast4());
        assertEquals(Direction.CREDIT, txn.direction());
        assertEquals(new BigDecimal("45000.00"), txn.amount());
        assertEquals("SALARY CREDIT", txn.merchant());
        assertEquals(
                OffsetDateTime.parse("2026-07-01T09:02:00+05:30"),
                txn.occurredAt());
        assertEquals("m-test-credit", txn.sourceMessageId());
    }

    @Test
    void rejectsNonEmailMessage() {
        RawMessage message = new RawMessage(
                "m-test-sms",
                "sms",
                "AD-HDFCBK-S",
                OffsetDateTime.parse("2026-07-02T11:49:00+05:30"),
                "dev-test",
                "Rs.76.49 debited from a/c **4821");

        assertTrue(parser.parse(message).isEmpty());
    }
}