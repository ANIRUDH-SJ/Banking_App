package com.netbanking.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.netbanking.discovery.OtpDeliveryClient;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

class OtpChannelDeliveryServiceTest {
    @Test
    void deliversTheCodeThroughEmailAndSmsWithoutReturningIt() {
        var email = mock(EmailService.class);
        var sms = mock(SmsService.class);
        var command =
                new OtpDeliveryClient.Command(
                        "challenge-1",
                        7L,
                        "asha@example.com",
                        "+919876543210",
                        "FUND_TRANSFER",
                        "123456",
                        Instant.parse("2026-09-29T10:30:00Z"));

        var receipt = new OtpChannelDeliveryService(email, sms).deliver(command);

        var emailBody = ArgumentCaptor.forClass(String.class);
        verify(email)
                .send(eq("asha@example.com"), eq("Your banking verification code"), emailBody.capture());
        verify(sms).send(eq("+919876543210"), contains("123456"));
        assertThat(emailBody.getValue()).contains("123456", "10:30 UTC", "Do not share");
        assertThat(receipt.challengeId()).isEqualTo("challenge-1");
        assertThat(receipt.channels()).containsExactly("EMAIL", "SMS");
        assertThat(receipt.toString()).doesNotContain("123456");
    }

    @Test
    void deliversEmailOnlyWhenNoMobileNumberExists() {
        var email = mock(EmailService.class);
        var sms = mock(SmsService.class);
        var command =
                new OtpDeliveryClient.Command(
                        "challenge-2",
                        7L,
                        "asha@example.com",
                        null,
                        "LOGIN",
                        "654321",
                        Instant.parse("2026-09-29T10:30:00Z"));

        var receipt = new OtpChannelDeliveryService(email, sms).deliver(command);

        verify(email).send(eq("asha@example.com"), anyString(), contains("654321"));
        verifyNoInteractions(sms);
        assertThat(receipt.channels()).containsExactly("EMAIL");
    }
}
