package com.netbanking.card.api;

import com.netbanking.card.security.PinTransportKeys;
import com.netbanking.card.service.CardPinService;
import com.netbanking.card.service.CardService;
import com.netbanking.card.service.CreditCardService;
import com.netbanking.common.api.PagedResponse;
import com.netbanking.security.SecurityContextHelper;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cards")
public class CardController {

    private final CardService cardService;
    private final CardPinService pins;
    private final CreditCardService credit;
    private final PinTransportKeys pinKeys;

    public CardController(
            CardService cardService, CardPinService pins, CreditCardService credit, PinTransportKeys pinKeys) {
        this.cardService = cardService;
        this.pins = pins;
        this.credit = credit;
        this.pinKeys = pinKeys;
    }

    @GetMapping
    public List<CardResponse> getCards() {
        return cardService.getCards(SecurityContextHelper.currentUserId());
    }

    /** The public key a browser uses to encrypt a PIN before sending it. */
    @GetMapping("/pin-key")
    public PinKeyResponse pinKey() {
        return new PinKeyResponse(
                pinKeys.keyId(), PinTransportKeys.ALGORITHM, pinKeys.publicKey(), pinKeys.maxAgeSeconds());
    }

    @GetMapping("/{cardId}")
    public CardResponse getCard(@PathVariable Long cardId) {
        return cardService.getCard(SecurityContextHelper.currentUserId(), cardId);
    }

    @PatchMapping("/{cardId}/status")
    public CardResponse changeStatus(
            @PathVariable Long cardId, @Valid @RequestBody ChangeCardStatusRequest request) {
        return cardService.changeStatus(
                SecurityContextHelper.currentUserId(), cardId, request.action());
    }

    @PostMapping("/{cardId}/reveal")
    public ResponseEntity<CardDetailsResponse> reveal(@PathVariable Long cardId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(cardService.reveal(SecurityContextHelper.currentUserId(), cardId));
    }

    @PutMapping("/{cardId}/pin")
    public CardResponse setPin(@PathVariable Long cardId, @Valid @RequestBody SetCardPinRequest request) {
        return pins.setPin(SecurityContextHelper.currentUserId(), cardId, request);
    }

    @GetMapping("/{cardId}/transactions")
    public PagedResponse<CardTransactionResponse> transactions(
            @PathVariable Long cardId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return credit.transactions(SecurityContextHelper.currentUserId(), cardId, page, size);
    }

    @GetMapping("/{cardId}/transactions/{transactionId}/emi-options")
    public EmiOptionsResponse emiOptions(@PathVariable Long cardId, @PathVariable Long transactionId) {
        return credit.emiOptions(SecurityContextHelper.currentUserId(), cardId, transactionId);
    }

    @PostMapping("/{cardId}/transactions/{transactionId}/emi")
    public CardTransactionResponse convertToEmi(
            @PathVariable Long cardId,
            @PathVariable Long transactionId,
            @Valid @RequestBody ConvertToEmiRequest request) {
        return credit.convert(
                SecurityContextHelper.currentUserId(),
                cardId,
                transactionId,
                request.tenureMonths(),
                request.idempotencyKey());
    }

    @GetMapping("/{cardId}/emi-plans")
    public List<EmiPlanResponse> emiPlans(@PathVariable Long cardId) {
        return credit.plans(SecurityContextHelper.currentUserId(), cardId);
    }
}
