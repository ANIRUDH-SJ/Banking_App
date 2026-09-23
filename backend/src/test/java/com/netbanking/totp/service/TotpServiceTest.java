package com.netbanking.totp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.common.exception.ConflictException;
import com.netbanking.totp.domain.UserTotp;
import com.netbanking.totp.repository.UserTotpRepository;
import com.netbanking.user.domain.AppUser;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TotpServiceTest {

    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    @Mock private UserTotpRepository repository;
    @Mock private TotpSecretCipher cipher;
    @Mock private SecretGenerator secretGenerator;
    @Mock private CodeVerifier verifier;
    @Mock private QrGenerator qrGenerator;

    @Test
    void returnsAuthenticatorCompatibleUriQrImageAndManualKey() throws Exception {
        AppUser user = user();
        when(secretGenerator.generate()).thenReturn(SECRET);
        when(cipher.encrypt(SECRET)).thenReturn("encrypted-secret");
        when(qrGenerator.generate(any(QrData.class)))
                .thenReturn("png-content".getBytes(StandardCharsets.UTF_8));
        when(qrGenerator.getImageMimeType()).thenReturn("image/png");

        var response = service().beginSetup(user);

        assertThat(response.provisioningUri()).startsWith("otpauth://totp/");
        assertThat(response.provisioningUri()).contains("secret=" + SECRET);
        assertThat(response.provisioningUri()).contains("issuer=Internet%20Banking");
        assertThat(response.provisioningUri()).contains("digits=6");
        assertThat(response.provisioningUri()).contains("period=30");
        assertThat(response.qrCodeDataUri()).startsWith("data:image/png;base64,");
        assertThat(response.manualEntryKey()).isEqualTo(SECRET);
        ArgumentCaptor<UserTotp> saved = ArgumentCaptor.forClass(UserTotp.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSecretCiphertext()).isEqualTo("encrypted-secret");
        assertThat(saved.getValue().isEnabled()).isFalse();
    }

    @Test
    void confirmsSetupOnlyWhenAuthenticatorCodeMatches() {
        AppUser user = user();
        UserTotp credential = new UserTotp(7L, "encrypted-secret");
        when(repository.findById(7L)).thenReturn(Optional.of(credential));
        when(cipher.decrypt("encrypted-secret")).thenReturn(SECRET);
        when(verifier.isValidCode(SECRET, "123456")).thenReturn(true);

        service().confirmSetup(user, "123456");

        assertThat(credential.isEnabled()).isTrue();
    }

    @Test
    void rejectsSetupReplacementForAnEnabledAuthenticator() {
        AppUser user = user();
        UserTotp credential = new UserTotp(7L, "encrypted-secret");
        credential.confirm();
        when(repository.findById(7L)).thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> service().beginSetup(user))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already configured");
    }

    private TotpService service() {
        return new TotpService(repository, cipher, "Internet Banking",
                secretGenerator, verifier, qrGenerator);
    }

    private static AppUser user() {
        AppUser user = new AppUser("asha", "asha@example.com", "password-hash");
        ReflectionTestUtils.setField(user, "userId", 7L);
        return user;
    }
}
