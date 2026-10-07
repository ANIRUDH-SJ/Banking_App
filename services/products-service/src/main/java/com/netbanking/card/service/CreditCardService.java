package com.netbanking.card.service;

import com.netbanking.audit.service.AuditLogService;
import com.netbanking.card.api.CardTransactionResponse;
import com.netbanking.card.api.EmiOptionsResponse;
import com.netbanking.card.api.EmiPlanResponse;
import com.netbanking.card.domain.BankCard;
import com.netbanking.card.domain.CardEmiPlan;
import com.netbanking.card.domain.CardStatus;
import com.netbanking.card.domain.CardTransaction;
import com.netbanking.card.repository.CardEmiPlanRepository;
import com.netbanking.card.repository.CardTransactionRepository;
import com.netbanking.common.api.PagedResponse;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.ResourceNotFoundException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Credit-card transaction history and conversion of eligible purchases into EMI plans. */
@Service
@Transactional(readOnly = true)
public class CreditCardService {
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("postedAt"), Sort.Order.desc("cardTransactionId"));

    private final CardService cards;
    private final CardTransactionRepository transactions;
    private final CardEmiPlanRepository plans;
    private final EmiCalculator calculator;
    private final AuditLogService audit;
    private final BigDecimal minimumEmiAmount;
    private final int emiWindowDays;

    public CreditCardService(
            CardService cards,
            CardTransactionRepository transactions,
            CardEmiPlanRepository plans,
            EmiCalculator calculator,
            AuditLogService audit,
            @Value("${app.cards.emi.min-amount:2500}") BigDecimal minimumEmiAmount,
            @Value("${app.cards.emi.window-days:60}") int emiWindowDays) {
        this.cards = cards;
        this.transactions = transactions;
        this.plans = plans;
        this.calculator = calculator;
        this.audit = audit;
        this.minimumEmiAmount = minimumEmiAmount;
        this.emiWindowDays = emiWindowDays;
    }

    public PagedResponse<CardTransactionResponse> transactions(Long userId, Long cardId, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("Page must be non-negative and size between 1 and 100.");
        BankCard card = creditCard(userId, cardId);
        Page<CardTransaction> rows = transactions.findByCardId(card.getCardId(), PageRequest.of(page, size, NEWEST_FIRST));
        Map<Long, CardEmiPlan> converted =
                plans.findByCardTransactionIdIn(rows.map(CardTransaction::getCardTransactionId).toList()).stream()
                        .collect(Collectors.toMap(CardEmiPlan::getCardTransactionId, Function.identity()));
        return PagedResponse.from(rows.map(row -> toResponse(card, row, converted.get(row.getCardTransactionId()))));
    }

    public EmiOptionsResponse emiOptions(Long userId, Long cardId, Long transactionId) {
        BankCard card = creditCard(userId, cardId);
        CardTransaction row = eligible(card, transactionId);
        return new EmiOptionsResponse(
                toResponse(card, row, null),
                calculator.options(row.getAmount()).stream()
                        .map(o -> new EmiOptionsResponse.Option(o.tenureMonths(), o.annualInterestRate(),
                                o.monthlyInstalment(), o.totalInterest(), o.totalPayable()))
                        .toList());
    }

    @Transactional
    public CardTransactionResponse convert(
            Long userId, Long cardId, Long transactionId, int tenureMonths, String requestKey) {
        BankCard card = creditCard(userId, cardId);
        String key = requestKey.strip();
        var prior = plans.findByCardIdAndRequestKey(card.getCardId(), key);
        if (prior.isPresent()) {
            CardEmiPlan plan = prior.get();
            if (!plan.getCardTransactionId().equals(transactionId) || plan.getTenureMonths() != tenureMonths)
                throw new ConflictException("This request key was already used for a different EMI conversion.");
            return toResponse(card, owned(card, transactionId), plan);
        }
        if (card.getCardStatus() != CardStatus.ACTIVE)
            throw new ConflictException("The card must be active to convert a purchase to EMI.");
        CardTransaction row = eligible(card, transactionId);
        EmiCalculator.Option option = calculator.option(row.getAmount(), tenureMonths);
        boolean billed = row.getBilledOn() != null;
        LocalDate due = card.getPaymentDueDate();
        LocalDate firstDue = billed && due != null && !due.isBefore(LocalDate.now()) ? due
                : (due == null ? LocalDate.now() : due).plusMonths(1);
        CardEmiPlan plan;
        try {
            plan = plans.saveAndFlush(new CardEmiPlan(
                    card.getCardId(), row.getCardTransactionId(), key, row.getAmount(), tenureMonths,
                    option.annualInterestRate(), option.monthlyInstalment(), option.totalInterest(),
                    option.totalPayable(), firstDue, LocalDateTime.now()));
        } catch (DataIntegrityViolationException concurrent) {
            throw new ConflictException("This purchase has already been converted to EMI.");
        }
        row.convertedToEmi();
        card.convertToEmi(row.getAmount(), option.monthlyInstalment(), option.totalInterest(), billed);
        audit.record(userId, "CARD_EMI_CONVERTED", "CARD", String.valueOf(cardId), "SUCCESS",
                "transaction=" + transactionId + ",tenure=" + tenureMonths);
        return toResponse(card, row, plan);
    }

    public List<EmiPlanResponse> plans(Long userId, Long cardId) {
        BankCard card = creditCard(userId, cardId);
        return plans.findByCardIdOrderByEmiPlanIdDesc(card.getCardId()).stream().map(CreditCardService::toPlan).toList();
    }

    private BankCard creditCard(Long userId, Long cardId) {
        BankCard card = cards.findOwnedCard(userId, cardId);
        if (!card.hasCreditAccount())
            throw new ConflictException("Billing details are available for credit cards only.");
        return card;
    }

    private CardTransaction owned(BankCard card, Long transactionId) {
        return transactions.findByCardTransactionIdAndCardId(transactionId, card.getCardId())
                .orElseThrow(() -> new ResourceNotFoundException("Card transaction was not found."));
    }

    private CardTransaction eligible(BankCard card, Long transactionId) {
        CardTransaction row = owned(card, transactionId);
        if (CardTransaction.CONVERTED_TO_EMI.equals(row.getStatus()))
            throw new ConflictException("This purchase has already been converted to EMI.");
        if (!isEligible(row))
            throw new ConflictException("Only posted purchases of at least INR " + minimumEmiAmount.toPlainString()
                    + " from the last " + emiWindowDays + " days can be converted to EMI.");
        return row;
    }

    private boolean isEligible(CardTransaction row) {
        return CardTransaction.PURCHASE.equals(row.getTransactionType())
                && CardTransaction.POSTED.equals(row.getStatus())
                && row.getAmount().compareTo(minimumEmiAmount) >= 0
                && row.getPostedAt().isAfter(LocalDateTime.now().minusDays(emiWindowDays));
    }

    private CardTransactionResponse toResponse(BankCard card, CardTransaction row, CardEmiPlan plan) {
        return new CardTransactionResponse(
                row.getCardTransactionId(),
                row.getReference(),
                row.getMerchantName(),
                row.getMerchantCategory(),
                row.getTransactionType(),
                row.getAmount(),
                row.getCurrencyCode(),
                row.getStatus(),
                row.getPostedAt(),
                row.getBilledOn() != null,
                plan == null && card.getCardStatus() == CardStatus.ACTIVE && isEligible(row),
                plan == null ? null : toPlan(plan));
    }

    private static EmiPlanResponse toPlan(CardEmiPlan plan) {
        return new EmiPlanResponse(
                plan.getEmiPlanId(),
                plan.getCardId(),
                plan.getCardTransactionId(),
                plan.getPrincipal(),
                plan.getTenureMonths(),
                plan.getAnnualInterestRate(),
                plan.getMonthlyInstalment(),
                plan.getTotalInterest(),
                plan.getTotalPayable(),
                plan.getInstalmentsPaid(),
                plan.getFirstDueDate(),
                plan.getStatus(),
                plan.getCreatedAt());
    }
}
