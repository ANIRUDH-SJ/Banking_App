package com.netbanking.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {
    @Mock private JavaMailSender mailSender;

    @Test
    void sendsPlainTextEmailThroughTheConfiguredMailSender() {
        SmtpEmailService service =
                new SmtpEmailService(mailSender, " no-reply@bank.example ", true, "mailer", "secret");

        service.send("asha@example.com", "Payment received", "Your payment was received.");

        ArgumentCaptor<SimpleMailMessage> message =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("no-reply@bank.example");
        assertThat(message.getValue().getTo()).containsExactly("asha@example.com");
        assertThat(message.getValue().getSubject()).isEqualTo("Payment received");
        assertThat(message.getValue().getText()).isEqualTo("Your payment was received.");
    }

    @Test
    void rejectsAnEmptySenderAddress() {
        assertThatThrownBy(() -> new SmtpEmailService(mailSender, "  ", true, "mailer", "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An SMTP sender address is required.");
    }

    @Test
    void refusesAuthenticatedSmtpWithoutCredentials() {
        assertThatThrownBy(() -> new SmtpEmailService(mailSender, "from@example.com", true, "", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SMTP authentication requires a username and password.");
    }

    @Test
    void allowsAnUnauthenticatedLocalSmtpServer() {
        SmtpEmailService service =
                new SmtpEmailService(mailSender, "from@example.com", false, "", "");

        service.send("recipient@example.com", "Test", "Body");

        verify(mailSender).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }

    @Test
    void deliversOverTheSmtpProtocol() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(5000);
            CompletableFuture<String> received = CompletableFuture.supplyAsync(() -> {
                try (var socket = server.accept();
                        var input = new BufferedReader(new InputStreamReader(
                                socket.getInputStream(), StandardCharsets.UTF_8));
                        var output = new BufferedWriter(new OutputStreamWriter(
                                socket.getOutputStream(), StandardCharsets.UTF_8))) {
                    socket.setSoTimeout(5000);
                    output.write("220 localhost ESMTP\r\n");
                    output.flush();
                    StringBuilder message = new StringBuilder();
                    boolean readingData = false;
                    String line;
                    while ((line = input.readLine()) != null) {
                        String response;
                        if (readingData) {
                            if (line.equals(".")) {
                                readingData = false;
                                response = "250 accepted\r\n";
                            } else {
                                message.append(line).append('\n');
                                continue;
                            }
                        } else if (line.startsWith("DATA")) {
                            readingData = true;
                            response = "354 end with dot\r\n";
                        } else if (line.startsWith("QUIT")) {
                            output.write("221 bye\r\n");
                            output.flush();
                            return message.toString();
                        } else {
                            response = "250 localhost\r\n";
                        }
                        output.write(response);
                        output.flush();
                    }
                    return message.toString();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });

            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost("127.0.0.1");
            sender.setPort(server.getLocalPort());
            SmtpEmailService service = new SmtpEmailService(
                    sender, "from@example.com", false, "", "");
            service.send("recipient@example.com", "Test delivery", "Message body");

            assertThat(received.get(5, TimeUnit.SECONDS))
                    .contains("To: recipient@example.com", "Subject: Test delivery", "Message body");
        }
    }
}
