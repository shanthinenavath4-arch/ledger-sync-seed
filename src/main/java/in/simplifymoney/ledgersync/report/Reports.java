package in.simplifymoney.ledgersync.report;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class Reports {

    private Reports() {}

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(2);

    public static Map<String, Object> summary(
            List<NormalizedTxn> ledger) {

        Map<String, Object> accounts = new LinkedHashMap<>();

        for (String acct : new TreeSet<>(
                ledger.stream()
                        .map(NormalizedTxn::accountLast4)
                        .toList())) {

            BigDecimal spend = ZERO;
            BigDecimal income = ZERO;
            BigDecimal microTotal = ZERO;
            BigDecimal transferredOut = ZERO;
            BigDecimal transferredIn = ZERO;
            int microCount = 0;

            for (NormalizedTxn t : ledger) {

                if (!t.accountLast4().equals(acct)) {
                    continue;
                }

                switch (t.category()) {

                    case SPEND ->
                            spend = spend.add(t.amount());

                    case INCOME ->
                            income = income.add(t.amount());

                    case MICRO -> {
                        microCount++;
                        microTotal = microTotal.add(t.amount());
                    }

                    case TRANSFER -> {
                        if (t.direction() == Direction.DEBIT) {
                            transferredOut =
                                    transferredOut.add(t.amount());
                        } else {
                            transferredIn =
                                    transferredIn.add(t.amount());
                        }
                    }
                }
            }

            Map<String, Object> a = new LinkedHashMap<>();

            a.put("spend", spend.toPlainString());
            a.put("income", income.toPlainString());
            a.put("micro_count", microCount);
            a.put("micro_total", microTotal.toPlainString());
            a.put("transferred_out",
                    transferredOut.toPlainString());
            a.put("transferred_in",
                    transferredIn.toPlainString());

            accounts.put(acct, a);
        }

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("accounts", accounts);

        return doc;
    }

    public static Map<String, Object> ledgerDocument(
            List<NormalizedTxn> ledger) {

        List<Object> rows = ledger.stream().map(t -> {

            Map<String, Object> r = new LinkedHashMap<>();

            r.put("account_last4", t.accountLast4());
            r.put("occurred_at", t.occurredAt().toString());
            r.put("direction",
                    t.direction().name().toLowerCase());
            r.put("amount", t.amount().toPlainString());
            r.put("category", t.category().name());
            r.put("merchant", t.merchant());
            r.put("source_message_ids",
                    t.sourceMessageIds());

            return (Object) r;

        }).toList();

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("transactions", rows);

        return doc;
    }

    /**
     * Reconciles the actual ledger against corpus-a-totals.json.
     *
     * The expected values are supplied by the corpus totals file rather
     * than being silently hard-coded into the ledger calculations.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> reconciliation(
            List<NormalizedTxn> ledger,
            Map<String, Object> expectedDocument) {

        Map<String, Object> result = new LinkedHashMap<>();
        List<Object> discrepancies = new ArrayList<>();

        Map<String, Object> expectedAccounts =
                (Map<String, Object>)
                        expectedDocument.get("accounts");

        Map<String, Object> actualSummary =
                summary(ledger);

        Map<String, Object> actualAccounts =
                (Map<String, Object>)
                        actualSummary.get("accounts");

        for (String acct :
                new TreeSet<>(expectedAccounts.keySet())) {

            Map<String, Object> expected =
                    (Map<String, Object>)
                            expectedAccounts.get(acct);

            Map<String, Object> actual =
                    actualAccounts.containsKey(acct)
                            ? (Map<String, Object>)
                                    actualAccounts.get(acct)
                            : new LinkedHashMap<>();

            int expectedTransactions =
                    intValue(expected.get("transactions_expected"));

            int actualTransactions =
                    (int) ledger.stream()
                            .filter(t ->
                                    t.accountLast4()
                                            .equals(acct))
                            .count();

            compare(
                    discrepancies,
                    acct,
                    "transactions",
                    String.valueOf(expectedTransactions),
                    String.valueOf(actualTransactions));

            compareField(
                    discrepancies,
                    acct,
                    "spend",
                    expected,
                    actual);

            compareField(
                    discrepancies,
                    acct,
                    "income",
                    expected,
                    actual);

            compareField(
                    discrepancies,
                    acct,
                    "micro_total",
                    expected,
                    actual);

            compareField(
                    discrepancies,
                    acct,
                    "transferred_out",
                    expected,
                    actual);

            compareField(
                    discrepancies,
                    acct,
                    "transferred_in",
                    expected,
                    actual);

            BigDecimal opening =
                    decimalValue(
                            expected.get("opening_balance"));

            BigDecimal income =
                    decimalValue(
                            actual.getOrDefault(
                                    "income", "0.00"));

            BigDecimal spend =
                    decimalValue(
                            actual.getOrDefault(
                                    "spend", "0.00"));

            BigDecimal micro =
                    decimalValue(
                            actual.getOrDefault(
                                    "micro_total", "0.00"));

            BigDecimal transferredIn =
                    decimalValue(
                            actual.getOrDefault(
                                    "transferred_in",
                                    "0.00"));

            BigDecimal transferredOut =
                    decimalValue(
                            actual.getOrDefault(
                                    "transferred_out",
                                    "0.00"));

            BigDecimal calculatedClosing =
                    opening
                            .add(income)
                            .add(transferredIn)
                            .subtract(spend)
                            .subtract(micro)
                            .subtract(transferredOut)
                            .setScale(2);

            BigDecimal expectedClosing =
                    decimalValue(
                            expected.get("closing_balance"));

            compare(
                    discrepancies,
                    acct,
                    "closing_balance",
                    expectedClosing.toPlainString(),
                    calculatedClosing.toPlainString());
        }


        result.put("reconciled",
                discrepancies.isEmpty());

        result.put("discrepancy_count",
                discrepancies.size());

        result.put("discrepancies",
                discrepancies);

        return result;
    }

    private static void compareField(
            List<Object> discrepancies,
            String acct,
            String field,
            Map<String, Object> expected,
            Map<String, Object> actual) {

        String expectedValue =
                String.valueOf(
                        expected.get(field));

        String actualValue =
                String.valueOf(
                        actual.getOrDefault(
                                field,
                                "0.00"));

        BigDecimal expectedDecimal =
                decimalValue(expectedValue);

        BigDecimal actualDecimal =
                decimalValue(actualValue);

        compare(
                discrepancies,
                acct,
                field,
                expectedDecimal.toPlainString(),
                actualDecimal.toPlainString());
    }

    private static void compare(
            List<Object> discrepancies,
            String acct,
            String field,
            String expected,
            String actual) {

        if (expected.equals(actual)) {
            return;
        }

        Map<String, Object> difference =
                new LinkedHashMap<>();

        difference.put("account_last4", acct);
        difference.put("field", field);
        difference.put("expected", expected);
        difference.put("actual", actual);

        discrepancies.add(difference);
    }

    private static BigDecimal decimalValue(Object value) {

        if (value == null) {
            return ZERO;
        }

        return new BigDecimal(
                String.valueOf(value))
                .setScale(2);
    }

    private static int intValue(Object value) {

        if (value == null) {
            return 0;
        }

        return Integer.parseInt(
                String.valueOf(value));
    }

    public static Map<Category, BigDecimal> byCategory(
            List<NormalizedTxn> ledger) {

        Map<Category, BigDecimal> out =
                new LinkedHashMap<>();

        for (Category c : Category.values()) {
            out.put(c, ZERO);
        }

        for (NormalizedTxn t : ledger) {

            out.put(
                    t.category(),
                    out.get(t.category())
                            .add(t.amount()));
        }

        return out;
    }
}