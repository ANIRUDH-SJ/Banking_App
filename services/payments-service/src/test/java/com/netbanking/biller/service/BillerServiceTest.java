package com.netbanking.biller.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.netbanking.biller.domain.Biller;
import com.netbanking.biller.repository.BillerRepository;

import org.junit.jupiter.api.Test;
import java.util.List;

class BillerServiceTest {
    @Test
    void publishesReferenceRulesForThePaymentForm() {
        Biller biller = mock(Biller.class);
        when(biller.getReferencePattern()).thenReturn("^[0-9]{5,20}$");
        when(biller.getReferenceHint()).thenReturn("Enter 5 to 20 digits.");
        BillerRepository repository = mock(BillerRepository.class);
        when(repository.findAllActive()).thenReturn(List.of(biller));

        var response = new BillerService(repository).listActive().get(0);

        assertThat(response.referencePattern()).isEqualTo("^[0-9]{5,20}$");
        assertThat(response.referenceHint()).isEqualTo("Enter 5 to 20 digits.");
    }
}
