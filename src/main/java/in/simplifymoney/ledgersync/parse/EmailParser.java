package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmailParser implements MessageParser {

    private static final Pattern DATE = Pattern.compile(
            "Date:\\s*(?<when>.+?)\\n");

    private static final Pattern TRANSACTION = Pattern.compile(
            "Your account ending (?<acct>\\d{4}) has been "
                    + "(?<dir>debited|credited) with "
                    + "(?:Rs\\.?|INR)\\s*(?<amount>[0-9,]+(?:\\.[0-9]{2})?)\\.");

    private static final Pattern MERCHANT = Pattern.compile(
            "Merchant / Remarks:\\s*(?<merchant>.+)");

    private static final DateTimeFormatter EMAIL_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss xx");

    @Override
    public boolean supports(RawMessage m) {
        return "email".equals(m.channel());
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage m) {
        String body = m.body();

        Matcher transaction = TRANSACTION.matcher(body);
        if (!transaction.find()) {
            return Optional.empty();
        }

        Matcher date = DATE.matcher(body);
        if (!date.find()) {
            return Optional.empty();
        }

        Matcher merchant = MERCHANT.matcher(body);

        try {
            String acct = transaction.group("acct");

            Direction direction =
                    "debited".equalsIgnoreCase(transaction.group("dir"))
                            ? Direction.DEBIT
                            : Direction.CREDIT;

            BigDecimal amount = new BigDecimal(
                    transaction.group("amount").replace(",", ""))
                    .setScale(2);

            OffsetDateTime occurredAt =

            OffsetDateTime.parse(

                date.group("when").trim(),
                EMAIL_DATE
                ).withOffsetSameInstant(
                        java.time.ZoneOffset.ofHoursMinutes(5, 30)
                        );

           String merchantName = merchant.find()
          ? merchant.group("merchant").trim()
          : "";

            return Optional.of(new ParsedTxn(
                    acct,
                    occurredAt,
                    direction,
                    amount,
                    merchantName,
                    null,
                    m.messageId()));

        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}