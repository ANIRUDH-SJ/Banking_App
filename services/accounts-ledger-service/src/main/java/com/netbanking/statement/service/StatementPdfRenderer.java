package com.netbanking.statement.service;

import com.netbanking.transaction.api.TransactionResponse;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Renders an account statement as an A4 PDF. Only the last four digits of the account number are
 * printed, and the built-in Helvetica fonts are used, so text is limited to Latin-1.
 */
@Component
public class StatementPdfRenderer {
    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final Color INK = new Color(0x1c, 0x24, 0x2b);
    private static final Color MUTED = new Color(0x5f, 0x6b, 0x72);
    private static final Color PETROL = new Color(0x0f, 0x3d, 0x4a);
    private static final Color LINE = new Color(0xd9, 0xd6, 0xcc);
    private static final Color CREDIT = new Color(0x1f, 0x6b, 0x3a);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private static final float MARGIN = 42;
    private static final float WIDTH = PDRectangle.A4.getWidth();
    private static final float HEIGHT = PDRectangle.A4.getHeight();
    private static final float ROW = 26;
    /** Column left edges: date, details, type, status, debit, credit, balance; then the right edge. */
    private static final float[] COLUMNS = {MARGIN, 100, 250, 305, 360, 425, 490, WIDTH - MARGIN};

    public record Account(
            String accountType, String accountNumber, String currencyCode, String nickname) {}

    public record Period(LocalDate from, LocalDate to, String type, String status) {}

    public byte[] render(
            Account account, Period period, List<TransactionResponse> oldestFirst, LocalDateTime generatedAt) {
        try (PDDocument document = new PDDocument()) {
            describe(document, account, generatedAt);
            List<PDPage> pages = new ArrayList<>();
            Writer writer = new Writer(document, pages);
            writer.header(account, period, generatedAt);
            writer.summary(account.currencyCode(), oldestFirst);
            writer.tableHead();
            if (oldestFirst.isEmpty()) {
                writer.empty();
            }
            for (TransactionResponse row : oldestFirst) {
                writer.row(row);
            }
            writer.close();
            footers(document, pages, account);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException("The statement PDF could not be produced.", failure);
        }
    }

    private static void describe(PDDocument document, Account account, LocalDateTime generatedAt) {
        PDDocumentInformation info = document.getDocumentInformation();
        info.setTitle("Account statement " + masked(account.accountNumber()));
        info.setAuthor("ORACLE INTERNATIONAL BANK (OIB)");
        info.setCreator("ORACLE INTERNATIONAL BANK (OIB)");
        Calendar created = Calendar.getInstance();
        created.setTime(java.sql.Timestamp.valueOf(generatedAt));
        info.setCreationDate(created);
    }

    private static void footers(PDDocument document, List<PDPage> pages, Account account)
            throws IOException {
        for (int i = 0; i < pages.size(); i++) {
            try (PDPageContentStream text =
                    new PDPageContentStream(
                            document, pages.get(i), PDPageContentStream.AppendMode.APPEND, true)) {
                line(text, MARGIN, 40, WIDTH - MARGIN, 40, LINE);
                write(text, REGULAR, 7.5f, MUTED, MARGIN, 28,
                        "Computer-generated statement for account " + masked(account.accountNumber())
                                + ". Balances and entries are taken from the bank's ledger.");
                String page = "Page " + (i + 1) + " of " + pages.size();
                write(text, REGULAR, 7.5f, MUTED, WIDTH - MARGIN - width(REGULAR, 7.5f, page), 28, page);
            }
        }
    }

    /** Lays rows out top to bottom and starts a new page when the current one is full. */
    private static final class Writer {
        private final PDDocument document;
        private final List<PDPage> pages;
        private PDPageContentStream content;
        private float y;
        private boolean shaded;

        Writer(PDDocument document, List<PDPage> pages) throws IOException {
            this.document = document;
            this.pages = pages;
            newPage();
        }

        void header(Account account, Period period, LocalDateTime generatedAt) throws IOException {
            content.setNonStrokingColor(PETROL);
            content.addRect(0, HEIGHT - 8, WIDTH, 8);
            content.fill();
            write(content, BOLD, 9, PETROL, MARGIN, y, "ORACLE INTERNATIONAL BANK (OIB)");
            write(content, REGULAR, 8, MUTED, WIDTH - MARGIN - width(REGULAR, 8, "Generated " + STAMP.format(generatedAt)),
                    y, "Generated " + STAMP.format(generatedAt));
            y -= 30;
            write(content, BOLD, 20, INK, MARGIN, y, "Account statement");
            y -= 26;
            String title = account.nickname() == null || account.nickname().isBlank()
                    ? labelize(account.accountType()) + " account"
                    : account.nickname() + "  -  " + labelize(account.accountType()) + " account";
            write(content, BOLD, 11, INK, MARGIN, y, title);
            y -= 15;
            write(content, REGULAR, 9, MUTED, MARGIN, y,
                    "Account " + masked(account.accountNumber()) + "   |   Currency " + account.currencyCode());
            y -= 14;
            write(content, REGULAR, 9, MUTED, MARGIN, y, "Period " + range(period) + filters(period));
            y -= 22;
        }

        void summary(String currency, List<TransactionResponse> rows) throws IOException {
            BigDecimal credits = BigDecimal.ZERO;
            BigDecimal debits = BigDecimal.ZERO;
            for (TransactionResponse row : rows) {
                if ("CREDIT".equals(row.entryType())) credits = credits.add(row.amount());
                else debits = debits.add(row.amount());
            }
            String opening = "-";
            String closing = "-";
            if (!rows.isEmpty()) {
                TransactionResponse first = rows.get(0);
                BigDecimal before = "CREDIT".equals(first.entryType())
                        ? first.balanceAfter().subtract(first.amount())
                        : first.balanceAfter().add(first.amount());
                opening = money(before);
                closing = money(rows.get(rows.size() - 1).balanceAfter());
            }
            String[][] facts = {
                {"OPENING BALANCE", opening},
                {"TOTAL CREDITS", money(credits)},
                {"TOTAL DEBITS", money(debits)},
                {"CLOSING BALANCE", closing}
            };
            float box = (WIDTH - 2 * MARGIN) / facts.length;
            content.setStrokingColor(LINE);
            content.setLineWidth(0.75f);
            content.addRect(MARGIN, y - 44, WIDTH - 2 * MARGIN, 44);
            content.stroke();
            for (int i = 0; i < facts.length; i++) {
                float x = MARGIN + i * box;
                if (i > 0) line(content, x, y - 44, x, y, LINE);
                write(content, REGULAR, 7, MUTED, x + 12, y - 15, facts[i][0]);
                write(content, BOLD, 11, INK, x + 12, y - 33, facts[i][1]
                        + ("-".equals(facts[i][1]) ? "" : " " + currency));
            }
            y -= 58;
            write(content, REGULAR, 8, MUTED, MARGIN, y, rows.size() + (rows.size() == 1 ? " entry" : " entries")
                    + ", oldest first. Amounts are in " + currency + ".");
            y -= 16;
        }

        void tableHead() throws IOException {
            String[] labels = {"DATE", "DETAILS", "TYPE", "STATUS", "DEBIT", "CREDIT", "BALANCE"};
            content.setNonStrokingColor(new Color(0xf3, 0xf1, 0xea));
            content.addRect(MARGIN, y - 18, WIDTH - 2 * MARGIN, 18);
            content.fill();
            for (int i = 0; i < labels.length; i++) {
                boolean numeric = i >= 4;
                float x = numeric
                        ? COLUMNS[i + 1] - 6 - width(BOLD, 7, labels[i])
                        : COLUMNS[i] + (i == 0 ? 6 : 0);
                write(content, BOLD, 7, MUTED, x, y - 12, labels[i]);
            }
            y -= 18;
            shaded = false;
        }

        void empty() throws IOException {
            write(content, REGULAR, 9, MUTED, MARGIN + 6, y - 18,
                    "No entries match this period and these filters.");
            y -= ROW;
        }

        void row(TransactionResponse row) throws IOException {
            if (y - ROW < 56) {
                content.close();
                newPage();
                tableHead();
            }
            if (shaded) {
                content.setNonStrokingColor(new Color(0xfa, 0xf9, 0xf5));
                content.addRect(MARGIN, y - ROW, WIDTH - 2 * MARGIN, ROW);
                content.fill();
            }
            shaded = !shaded;
            float top = y - 11;
            float bottom = y - 21;
            write(content, REGULAR, 8, INK, COLUMNS[0] + 6, top, DATE.format(row.postedAt()));
            write(content, REGULAR, 7, MUTED, COLUMNS[0] + 6, bottom, TIME.format(row.postedAt()));
            String details = row.narration() == null || row.narration().isBlank()
                    ? labelize(row.type()) : row.narration();
            write(content, REGULAR, 8, INK, COLUMNS[1], top, fit(REGULAR, 8, details, COLUMNS[2] - COLUMNS[1] - 8));
            write(content, REGULAR, 7, MUTED, COLUMNS[1], bottom,
                    fit(REGULAR, 7, row.reference(), COLUMNS[2] - COLUMNS[1] - 8));
            write(content, REGULAR, 8, INK, COLUMNS[2], top, fit(REGULAR, 8, labelize(row.type()), COLUMNS[3] - COLUMNS[2] - 4));
            write(content, REGULAR, 8, INK, COLUMNS[3], top, fit(REGULAR, 8, labelize(row.status()), COLUMNS[4] - COLUMNS[3] - 4));
            boolean credit = "CREDIT".equals(row.entryType());
            right(content, credit ? REGULAR : BOLD, 8, INK, COLUMNS[5] - 6, top, credit ? "" : money(row.amount()));
            right(content, credit ? BOLD : REGULAR, 8, CREDIT, COLUMNS[6] - 6, top, credit ? money(row.amount()) : "");
            right(content, REGULAR, 8, INK, COLUMNS[7] - 6, top, money(row.balanceAfter()));
            line(content, MARGIN, y - ROW, WIDTH - MARGIN, y - ROW, LINE);
            y -= ROW;
        }

        void close() throws IOException {
            content.close();
        }

        private void newPage() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            pages.add(page);
            content = new PDPageContentStream(document, page);
            y = HEIGHT - 40;
            if (pages.size() > 1) {
                write(content, BOLD, 9, PETROL, MARGIN, y, "Account statement (continued)");
                y -= 20;
            }
        }
    }

    private static String range(Period period) {
        if (period.from() == null && period.to() == null) return "all available history";
        if (period.from() == null) return "up to " + DATE.format(period.to());
        if (period.to() == null) return "from " + DATE.format(period.from());
        return DATE.format(period.from()) + " to " + DATE.format(period.to());
    }

    private static String filters(Period period) {
        StringBuilder text = new StringBuilder();
        if (period.type() != null) text.append("   |   Type ").append(labelize(period.type()));
        if (period.status() != null) text.append("   |   Status ").append(labelize(period.status()));
        return text.toString();
    }

    static String masked(String accountNumber) {
        String digits = accountNumber == null ? "" : accountNumber.strip();
        return "XXXX " + (digits.length() <= 4 ? digits : digits.substring(digits.length() - 4));
    }

    private static String labelize(String value) {
        if (value == null || value.isBlank()) return "";
        String spaced = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String money(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return format.format(amount);
    }

    private static void write(
            PDPageContentStream content, PDFont font, float size, Color color, float x, float y, String text)
            throws IOException {
        if (text == null || text.isEmpty()) return;
        content.beginText();
        content.setFont(font, size);
        content.setNonStrokingColor(color);
        content.newLineAtOffset(x, y);
        content.showText(latin1(text));
        content.endText();
    }

    private static void right(
            PDPageContentStream content, PDFont font, float size, Color color, float rightEdge, float y, String text)
            throws IOException {
        write(content, font, size, color, rightEdge - width(font, size, text), y, text);
    }

    private static void line(
            PDPageContentStream content, float x1, float y1, float x2, float y2, Color color)
            throws IOException {
        content.setStrokingColor(color);
        content.setLineWidth(0.5f);
        content.moveTo(x1, y1);
        content.lineTo(x2, y2);
        content.stroke();
    }

    private static float width(PDFont font, float size, String text) {
        try {
            return font.getStringWidth(latin1(text)) / 1000 * size;
        } catch (IOException impossible) {
            return 0;
        }
    }

    private static String fit(PDFont font, float size, String text, float available) {
        String safe = latin1(text);
        if (width(font, size, safe) <= available) return safe;
        String ellipsis = "...";
        int end = safe.length();
        while (end > 0 && width(font, size, safe.substring(0, end) + ellipsis) > available) end--;
        return safe.substring(0, end).stripTrailing() + ellipsis;
    }

    /** Keeps printable Latin-1 characters, which the standard PDF fonts can draw. */
    static String latin1(String text) {
        if (text == null) return "";
        StringBuilder safe = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == '\u20b9') safe.append("INR ");
            else if (c >= 0x20 && c <= 0x7e || c >= 0xa0 && c <= 0xff) safe.append(c);
            else if (Character.isWhitespace(c)) safe.append(' ');
            else safe.append('?');
        }
        return safe.toString();
    }
}
