package com.netbanking.biller.service;

import com.netbanking.biller.api.BillerResponse;
import com.netbanking.biller.domain.Biller;
import com.netbanking.biller.repository.BillerRepository;
import com.netbanking.common.exception.ResourceNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BillerService {
    private final BillerRepository repository;

    public BillerService(BillerRepository repository) {
        this.repository = repository;
    }

    public List<BillerResponse> listActive() {
        return repository.findAllActive().stream().map(this::toResponse).toList();
    }

    public Biller requireActiveAndAmount(Long id, BigDecimal amount) {
        Biller biller =
                repository
                        .findById(id)
                        .filter(Biller::isActive)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Active biller was not found."));
        if (amount.compareTo(biller.getMinAmount()) < 0
                || amount.compareTo(biller.getMaxAmount()) > 0)
            throw new IllegalArgumentException("Amount is outside the biller's permitted range.");
        return biller;
    }

    private BillerResponse toResponse(Biller b) {
        return new BillerResponse(
                b.getBillerId(),
                b.getBillerCode(),
                b.getBillerName(),
                b.getCategory(),
                b.getReferenceLabel(),
                b.getMinAmount(),
                b.getMaxAmount());
    }
}
