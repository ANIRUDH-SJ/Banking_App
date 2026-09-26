package com.netbanking.totp.service;

import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.UnauthorizedException;
import com.netbanking.totp.api.TotpSetupResponse;
import com.netbanking.totp.domain.UserTotp;
import com.netbanking.totp.repository.UserTotpRepository;
import com.netbanking.user.domain.AppUser;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.TimeProvider;
import dev.samstevens.totp.util.Utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@Transactional
public class TotpService {
    private final UserTotpRepository repository;
    private final TotpSecretCipher cipher;
    private final String issuer;
    private final SecretGenerator secretGenerator;
    private final CodeVerifier verifier;
    private final CodeGenerator codeGenerator;
    private final QrGenerator qrGenerator;
    private final TimeProvider timeProvider;

    public TotpService(
            UserTotpRepository repository,
            TotpSecretCipher cipher,
            @Value("${app.security.totp.issuer:Internet Banking}") String issuer,
            SecretGenerator secretGenerator,
            CodeVerifier verifier,
            CodeGenerator codeGenerator,
            QrGenerator qrGenerator,
            TimeProvider timeProvider) {
        this.repository = repository;
        this.cipher = cipher;
        this.issuer = issuer;
        this.secretGenerator = secretGenerator;
        this.verifier = verifier;
        this.codeGenerator = codeGenerator;
        this.qrGenerator = qrGenerator;
        this.timeProvider = timeProvider;
    }

    public TotpSetupResponse beginSetup(AppUser user) {
        UserTotp credential = repository.findById(user.getUserId()).orElse(null);
        if (credential != null && credential.isEnabled()) {
            throw new ConflictException("Authenticator is already configured.");
        }
        String secret = secretGenerator.generate();
        String encryptedSecret = cipher.encrypt(secret);
        if (credential == null) {
            credential = new UserTotp(user.getUserId(), encryptedSecret);
        } else {
            credential.replaceSecret(encryptedSecret);
        }
        repository.save(credential);
        QrData data =
                new QrData.Builder()
                        .label(user.getUsername())
                        .secret(secret)
                        .issuer(issuer)
                        .algorithm(HashingAlgorithm.SHA1)
                        .digits(6)
                        .period(30)
                        .build();
        return new TotpSetupResponse(
                data.getUri(), generateQrCode(data), secret, issuer, user.getUsername());
    }

    public void confirmSetup(AppUser user, String code) {
        UserTotp credential =
                repository
                        .findById(user.getUserId())
                        .orElseThrow(
                                () ->
                                        new UnauthorizedException(
                                                "Authenticator setup was not started."));
        if (!isValid(credential, code))
            throw new UnauthorizedException("Authenticator code is invalid.");
        credential.confirm();
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(AppUser user) {
        return repository.findById(user.getUserId()).map(UserTotp::isEnabled).orElse(false);
    }

    public void verifyLogin(AppUser user, String code) {
        UserTotp credential =
                repository
                        .findByUserIdForUpdate(user.getUserId())
                        .orElseThrow(
                                () ->
                                        new UnauthorizedException(
                                                "Authenticator is not configured."));
        if (!credential.isEnabled())
            throw new UnauthorizedException("Authenticator code is invalid.");
        long matchedTimeStep = matchedTimeStep(credential, code);
        if (matchedTimeStep < 0) throw new UnauthorizedException("Authenticator code is invalid.");
        if (!credential.acceptTimeStep(matchedTimeStep)) {
            throw new UnauthorizedException("Authenticator code was already used.");
        }
    }

    private boolean isValid(UserTotp credential, String code) {
        return verifier.isValidCode(cipher.decrypt(credential.getSecretCiphertext()), code);
    }

    private long matchedTimeStep(UserTotp credential, String code) {
        if (code == null || !code.matches("[0-9]{6}")) return -1;
        String secret = cipher.decrypt(credential.getSecretCiphertext());
        long currentTimeStep = timeProvider.getTime() / 30;
        // Prefer the newest match if two windows happen to produce the same six-digit code.
        for (long timeStep = currentTimeStep + 1; timeStep >= currentTimeStep - 1; timeStep--) {
            try {
                String expected = codeGenerator.generate(secret, timeStep);
                if (MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.US_ASCII),
                        code.getBytes(StandardCharsets.US_ASCII))) return timeStep;
            } catch (CodeGenerationException exception) {
                throw new IllegalStateException("Unable to verify authenticator code.", exception);
            }
        }
        return -1;
    }

    private String generateQrCode(QrData data) {
        try {
            return Utils.getDataUriForImage(
                    qrGenerator.generate(data), qrGenerator.getImageMimeType());
        } catch (dev.samstevens.totp.exceptions.QrGenerationException exception) {
            throw new IllegalStateException("Unable to generate authenticator QR code.", exception);
        }
    }
}
