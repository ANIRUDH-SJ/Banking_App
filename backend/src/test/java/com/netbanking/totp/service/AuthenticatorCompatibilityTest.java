package com.netbanking.totp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.time.TimeProvider;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class AuthenticatorCompatibilityTest {

    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    @Test
    void generatedQrCodeDecodesToTheProvisioningUri() throws Exception {
        QrData data = new QrData.Builder()
                .label("asha")
                .secret(SECRET)
                .issuer("Internet Banking")
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        byte[] png = new ZxingPngQrGenerator().generate(data);

        var image = ImageIO.read(new ByteArrayInputStream(png));
        var bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        String decoded = new MultiFormatReader().decode(bitmap).getText();

        assertThat(decoded).isEqualTo(data.getUri());
    }

    @Test
    void standardSixDigitThirtySecondCodeVerifies() throws Exception {
        long fixedEpochSecond = 1_700_000_000L;
        TimeProvider timeProvider = () -> fixedEpochSecond;
        var generator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
        var verifier = new DefaultCodeVerifier(generator, timeProvider);
        verifier.setTimePeriod(30);
        verifier.setAllowedTimePeriodDiscrepancy(0);
        String code = generator.generate(SECRET, fixedEpochSecond / 30);

        assertThat(code).hasSize(6).containsOnlyDigits();
        assertThat(verifier.isValidCode(SECRET, code)).isTrue();
        assertThat(verifier.isValidCode(SECRET, "000000")).isFalse();
    }
}
