package com.netbanking.totp.service;

import com.netbanking.common.exception.UnauthorizedException;
import com.netbanking.totp.api.TotpSetupResponse;
import com.netbanking.totp.domain.UserTotp;
import com.netbanking.totp.repository.UserTotpRepository;
import com.netbanking.user.domain.AppUser;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TotpService {
    private final UserTotpRepository repository;
    private final TotpSecretCipher cipher;
    private final String issuer;
    private final DefaultCodeVerifier verifier;
    public TotpService(UserTotpRepository repository, TotpSecretCipher cipher, @Value("${app.security.totp.issuer:Internet Banking}") String issuer) {
        this.repository = repository; this.cipher = cipher; this.issuer = issuer;
        this.verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6), new SystemTimeProvider());
        this.verifier.setTimePeriod(30); this.verifier.setAllowedTimePeriodDiscrepancy(1);
    }
    public TotpSetupResponse beginSetup(AppUser user) {
        String secret = new DefaultSecretGenerator().generate();
        UserTotp credential = repository.findById(user.getUserId()).orElse(new UserTotp(user.getUserId(), cipher.encrypt(secret)));
        if (credential.getUserId() != null && repository.existsById(user.getUserId())) credential.replaceSecret(cipher.encrypt(secret));
        repository.save(credential);
        QrData data = new QrData.Builder().label(user.getUsername()).secret(secret).issuer(issuer)
                .algorithm(HashingAlgorithm.SHA1).digits(6).period(30).build();
        return new TotpSetupResponse(data.getUri(), issuer, user.getUsername());
    }
    public void confirmSetup(AppUser user, String code) {
        UserTotp credential = repository.findById(user.getUserId()).orElseThrow(() -> new UnauthorizedException("Authenticator setup was not started."));
        if (!isValid(credential, code)) throw new UnauthorizedException("Authenticator code is invalid.");
        credential.confirm();
    }
    @Transactional(readOnly = true)
    public boolean isEnabled(AppUser user) { return repository.findById(user.getUserId()).map(UserTotp::isEnabled).orElse(false); }
    public void verifyLogin(AppUser user, String code) {
        UserTotp credential = repository.findById(user.getUserId()).orElseThrow(() -> new UnauthorizedException("Authenticator is not configured."));
        if (!credential.isEnabled() || !isValid(credential, code)) throw new UnauthorizedException("Authenticator code is invalid.");
    }
    private boolean isValid(UserTotp credential, String code) { return verifier.isValidCode(cipher.decrypt(credential.getSecretCiphertext()), code); }
}
