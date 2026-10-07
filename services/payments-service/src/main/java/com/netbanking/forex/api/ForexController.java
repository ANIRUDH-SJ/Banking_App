package com.netbanking.forex.api;

import com.netbanking.common.api.PagedResponse;
import com.netbanking.forex.service.ForexService;
import com.netbanking.payment.api.OtpChallengeResponse;
import com.netbanking.security.SecurityContextHelper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/forex")
public class ForexController {
    private final ForexService service;

    public ForexController(ForexService service) {
        this.service = service;
    }

    @GetMapping("/rates")
    public ForexRatesResponse rates() {
        return service.rates();
    }

    @GetMapping("/rates/convert")
    public ForexConversionPreview preview(
            @RequestParam String from, @RequestParam String to, @RequestParam BigDecimal amount) {
        return service.preview(from, to, amount);
    }

    @PostMapping("/quotes")
    public ForexQuoteResponse quote(@Valid @RequestBody CreateForexQuoteRequest request) {
        return service.quote(SecurityContextHelper.currentUserId(), request);
    }

    @PostMapping("/quotes/{quoteId}/otp-challenges")
    public OtpChallengeResponse challenge(@PathVariable String quoteId) {
        return service.challenge(SecurityContextHelper.currentUserId(), quoteId);
    }

    @PostMapping("/conversions")
    public ForexConversionResponse convert(@Valid @RequestBody CreateForexConversionRequest request) {
        return service.convert(SecurityContextHelper.currentUserId(), request);
    }

    @GetMapping("/conversions")
    public PagedResponse<ForexConversionResponse> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.history(SecurityContextHelper.currentUserId(), page, size);
    }

    @GetMapping("/conversions/{conversionId}")
    public ForexConversionResponse get(@PathVariable Long conversionId) {
        return service.get(SecurityContextHelper.currentUserId(), conversionId);
    }
}
