package com.netbanking.card.service;

import com.netbanking.card.domain.BankCard;
import com.netbanking.card.domain.CardNetwork;
import com.netbanking.card.domain.CardStatus;
import com.netbanking.card.domain.CardTransaction;
import com.netbanking.card.domain.CardType;
import com.netbanking.card.repository.BankCardRepository;
import com.netbanking.card.repository.CardTransactionRepository;
import com.netbanking.card.security.CardNumberCipher;
import com.netbanking.contracts.AccountSnapshot;
import com.netbanking.discovery.LedgerClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Local demonstrations only: the bank has no card-issuing integration, so a customer who opens
 * the cards screen without any card receives one debit and one credit card linked to their first
 * active INR account, with a short purchase history on the credit card. Enabled with
 * {@code app.cards.demo-issuance=true}; never enable it in a real deployment.
 */
@Component
@ConditionalOnProperty(name = "app.cards.demo-issuance", havingValue = "true")
public class DemoCardIssuer {
    private static final Logger log = LoggerFactory.getLogger(DemoCardIssuer.class);
    private static final BigDecimal CREDIT_LIMIT = new BigDecimal("150000.00");

    private final BankCardRepository cards;
    private final CardTransactionRepository transactions;
    private final CardNumberCipher numbers;
    private final LedgerClient ledger;
    private final SecureRandom random = new SecureRandom();

    public DemoCardIssuer(
            BankCardRepository cards,
            CardTransactionRepository transactions,
            CardNumberCipher numbers,
            LedgerClient ledger) {
        this.cards = cards;
        this.transactions = transactions;
        this.numbers = numbers;
        this.ledger = ledger;
        log.warn("Demo card issuance is enabled; customers without cards receive sample cards.");
    }

    List<BankCard> issue(Long userId, Long customerId) {
        AccountSnapshot account =
                ledger.accounts(userId).stream()
                        .filter(a -> "ACTIVE".equals(a.status()) && "INR".equals(a.currencyCode()))
                        .findFirst()
                        .orElse(null);
        if (account == null) return List.of();
        LocalDate today = LocalDate.now();
        BankCard debit = card(customerId, account.accountId(), CardType.DEBIT, CardNetwork.RUPAY, "652294", today);
        BankCard credit = card(customerId, account.accountId(), CardType.CREDIT, CardNetwork.VISA, "431940", today);
        cards.saveAndFlush(debit);
        cards.saveAndFlush(credit);
        history(credit, today);
        return List.of(debit, credit);
    }

    private BankCard card(
            Long customerId, Long accountId, CardType type, CardNetwork network, String bin, LocalDate today) {
        String number = luhn(bin);
        String token = "demo_" + UUID.randomUUID().toString().replace("-", "");
        BankCard card =
                new BankCard(
                        customerId,
                        accountId,
                        token,
                        number.substring(12),
                        type,
                        network,
                        today.getMonthValue(),
                        today.getYear() + 5,
                        CardStatus.INACTIVE);
        card.apply(com.netbanking.card.domain.CardStatusAction.ACTIVATE);
        if (numbers.enabled()) card.protectNumber(numbers.encrypt(number, token));
        return card;
    }

    /** A billing cycle that closed on the 15th, payable 20 days later, plus unbilled spends. */
    private void history(BankCard credit, LocalDate today) {
        LocalDate statement = today.getDayOfMonth() > 15
                ? today.withDayOfMonth(15)
                : today.minusMonths(1).withDayOfMonth(15);
        LocalDate due = statement.plusDays(20);
        Object[][] spends = {
            {"Croma Electronics", "ELECTRONICS", "38990.00", -24, CardTransaction.PURCHASE},
            {"IndiGo Airlines", "TRAVEL", "15870.00", -18, CardTransaction.PURCHASE},
            {"Amazon India", "SHOPPING", "12499.00", -12, CardTransaction.PURCHASE},
            {"Myntra", "SHOPPING", "4599.00", -9, CardTransaction.PURCHASE},
            {"Myntra refund", "SHOPPING", "1299.00", -6, CardTransaction.REFUND},
            {"Apollo Pharmacy", "HEALTH", "1180.00", -4, CardTransaction.PURCHASE},
            {"Payment received - thank you", "PAYMENT", "8000.00", -2, CardTransaction.PAYMENT},
            {"Swiggy", "FOOD", "640.00", 2, CardTransaction.PURCHASE},
            {"IRCTC", "TRAVEL", "3245.00", 4, CardTransaction.PURCHASE},
            {"BookMyShow", "ENTERTAINMENT", "1050.00", 6, CardTransaction.PURCHASE},
            {"Uber India", "TRAVEL", "412.00", 8, CardTransaction.PURCHASE},
        };
        BigDecimal billed = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        List<CardTransaction> rows = new ArrayList<>();
        for (Object[] spend : spends) {
            LocalDateTime postedAt = statement.plusDays((Integer) spend[3]).atTime(10 + rows.size(), 15);
            if (postedAt.isAfter(LocalDateTime.now())) continue;
            boolean isBilled = !postedAt.toLocalDate().isAfter(statement);
            BigDecimal amount = new BigDecimal((String) spend[2]);
            String type = (String) spend[4];
            BigDecimal signed = CardTransaction.PURCHASE.equals(type) ? amount : amount.negate();
            outstanding = outstanding.add(signed);
            if (isBilled) billed = billed.add(signed);
            rows.add(new CardTransaction(
                    credit.getCardId(),
                    reference(postedAt),
                    (String) spend[0],
                    (String) spend[1],
                    type,
                    amount,
                    CardTransaction.POSTED,
                    postedAt,
                    isBilled ? statement : null));
        }
        LocalDateTime pending = LocalDateTime.now().minusHours(3);
        rows.add(new CardTransaction(credit.getCardId(), reference(pending), "Zomato", "FOOD",
                CardTransaction.PURCHASE, new BigDecimal("520.00"), CardTransaction.PENDING, pending, null));
        outstanding = outstanding.add(new BigDecimal("520.00"));
        transactions.saveAll(rows);
        credit.openCreditAccount(CREDIT_LIMIT, outstanding.max(BigDecimal.ZERO), billed.max(BigDecimal.ZERO),
                statement, due);
    }

    private String reference(LocalDateTime postedAt) {
        return "CT" + postedAt.toLocalDate().toString().replace("-", "")
                + String.format("%010d", Math.floorMod(random.nextLong(), 10_000_000_000L));
    }

    private String luhn(String bin) {
        StringBuilder digits = new StringBuilder(bin);
        while (digits.length() < 15) digits.append(random.nextInt(10));
        int sum = 0;
        for (int i = 0; i < 15; i++) {
            int digit = digits.charAt(14 - i) - '0';
            if (i % 2 == 0) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
        }
        return digits.append((10 - sum % 10) % 10).toString();
    }
}
