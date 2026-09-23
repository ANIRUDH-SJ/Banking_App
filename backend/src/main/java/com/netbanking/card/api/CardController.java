package com.netbanking.card.api;

import com.netbanking.card.service.CardService;
import com.netbanking.security.SecurityContextHelper;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cards")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping
    public List<CardResponse> getCards() {
        return cardService.getCards(SecurityContextHelper.currentUserId());
    }

    @GetMapping("/{cardId}")
    public CardResponse getCard(@PathVariable Long cardId) {
        return cardService.getCard(SecurityContextHelper.currentUserId(), cardId);
    }

    @PatchMapping("/{cardId}/status")
    public CardResponse changeStatus(@PathVariable Long cardId,
                                     @Valid @RequestBody ChangeCardStatusRequest request) {
        return cardService.changeStatus(
                SecurityContextHelper.currentUserId(), cardId, request.action());
    }
}
