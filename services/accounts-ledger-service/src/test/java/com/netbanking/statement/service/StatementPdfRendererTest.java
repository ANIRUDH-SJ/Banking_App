package com.netbanking.statement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.netbanking.transaction.api.TransactionResponse;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

class StatementPdfRendererTest {
    private final StatementPdfRenderer renderer = new StatementPdfRenderer();
    private final StatementPdfRenderer.Account account =
            new StatementPdfRenderer.Account("SAVINGS", "100000000000427731", "INR", "Salary");

    @Test
    void printsPeriodTotalsAndEntriesButOnlyTheLastFourDigits() throws Exception {
        byte[] pdf = renderer.render(
                account,
                new StatementPdfRenderer.Period(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "TRANSFER", null),
                List.of(
                        row(1, "CREDIT", "50000.00", "50000.00", "Salary for September \u20b9"),
                        row(2, "DEBIT", "12500.50", "37499.50", "Rent to Landlord")),
                LocalDateTime.of(2026, 10, 7, 9, 30));

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        String text = text(pdf);
        assertThat(text)
                .contains("Account statement", "Salary", "XXXX 7731", "01 Sep 2026 to 30 Sep 2026", "Type Transfer")
                .contains("50,000.00", "12,500.50", "37,499.50", "Rent to Landlord", "Salary for September INR")
                .doesNotContain("100000000000427731");
    }

    @Test
    void continuesLongStatementsOnNumberedPages() throws Exception {
        List<TransactionResponse> rows = new ArrayList<>();
        for (int i = 1; i <= 80; i++) rows.add(row(i, "DEBIT", "10.00", "1000.00", "Coffee " + i));

        byte[] pdf = renderer.render(account, new StatementPdfRenderer.Period(null, null, null, null), rows,
                LocalDateTime.of(2026, 10, 7, 9, 30));

        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(2);
        }
        assertThat(text(pdf)).contains("all available history", "Page 1 of", "Coffee 80");
    }

    @Test
    void saysSoWhenNothingMatches() throws Exception {
        byte[] pdf = renderer.render(account, new StatementPdfRenderer.Period(null, LocalDate.of(2026, 1, 1), null, null),
                List.of(), LocalDateTime.of(2026, 10, 7, 9, 30));

        assertThat(text(pdf)).contains("No entries match", "up to 01 Jan 2026");
    }

    private static String text(byte[] pdf) throws Exception {
        try (var document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static TransactionResponse row(int id, String entryType, String amount, String balance, String narration) {
        return new TransactionResponse((long) id, (long) id, "TXN" + id, "TRANSFER", "COMPLETED", entryType,
                new BigDecimal(amount), "INR", new BigDecimal(balance), narration,
                LocalDateTime.of(2026, 9, 1, 10, 0).plusHours(id));
    }
}
