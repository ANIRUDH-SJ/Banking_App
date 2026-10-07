package com.netbanking.card.api;

import com.netbanking.card.service.CardPinService;
import com.netbanking.discovery.CardPinClient;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('SERVICE_payments-service')")
public class InternalCardPinController {
    private final CardPinService pins;

    public InternalCardPinController(CardPinService pins) {
        this.pins = pins;
    }

    @PostMapping("/internal/cards/pin-verifications")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody CardPinClient.Verification request) {
        pins.verifyForPayment(
                request.userId(),
                request.cardId(),
                request.accountId(),
                request.pinKeyId(),
                request.encryptedPin(),
                request.purpose());
    }
}
