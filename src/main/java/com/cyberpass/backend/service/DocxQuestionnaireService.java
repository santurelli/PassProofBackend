package com.cyberpass.backend.service;

import com.cyberpass.backend.dto.ExportRowRequest;
import com.cyberpass.backend.dto.QuestionRequest;
import com.cyberpass.backend.dto.QuestionResponse;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.poi.xwpf.usermodel.*;
import org.jboss.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service for parsing and exporting security questionnaires in Microsoft Word (.docx) format.
 * Utilizes Apache POI XWPF for document manipulation.
 */
@ApplicationScoped
public class DocxQuestionnaireService {

    private static final Logger LOG = Logger.getLogger(DocxQuestionnaireService.class);

    private static final Pattern NUMBERED_PATTERN = Pattern.compile("^(\\d+[\\.\\)]\\s+|Q\\s*\\d+[:\\.]?\\s*|Domanda\\s*\\d*[:\\.]?\\s*|Requirement\\s*\\d*[:\\.]?\\s*).*", Pattern.CASE_INSENSITIVE);

    @Inject
    QuestionnaireProcessingService questionnaireProcessingService;

    /**
     * Parses the Word (.docx) file, extracts questions from tables or formatted paragraphs,
     * processes all questions through the RAG AI engine, and returns the suggested responses.
     */
    public Uni<List<QuestionResponse>> parseAndProcessDocx(Path docxPath) {
        List<String> questions = new ArrayList<>();

        try (InputStream is = new FileInputStream(docxPath.toFile());
             XWPFDocument doc = new XWPFDocument(is)) {

            // 1. Primary Strategy: Extract questions from tables
            List<XWPFTable> tables = doc.getTables();
            if (tables != null && !tables.isEmpty()) {
                LOG.infof("Found %d table(s) in Word document. Parsing table rows...", tables.size());
                for (XWPFTable table : tables) {
                    extractQuestionsFromTable(table, questions);
                }
            }

            // 2. Secondary Strategy: If no questions found in tables, parse paragraphs
            if (questions.isEmpty()) {
                LOG.info("No questions found in tables. Parsing document paragraphs for numbered questions...");
                extractQuestionsFromParagraphs(doc.getParagraphs(), questions);
            }

        } catch (IOException e) {
            LOG.error("Failed to read uploaded Word (.docx) file", e);
            return Uni.createFrom().failure(e);
        }

        if (questions.isEmpty()) {
            return Uni.createFrom().failure(new IllegalArgumentException(
                "Nessuna domanda identificata nel documento Word (.docx). Assicurati che contenga una tabella o un elenco numerato di domande."
            ));
        }

        LOG.infof("Extracted %d questions from Word document. Initializing RAG processing pipeline...", questions.size());

        // Process all questions reactively in parallel
        List<Uni<QuestionResponse>> processingUnis = new ArrayList<>();
        for (String question : questions) {
            Uni<QuestionResponse> singleQueryUni = questionnaireProcessingService.processQuestion(new QuestionRequest(question, null))
                .onFailure().recoverWithItem(err -> {
                    LOG.errorf(err, "Failed to analyze question: '%s'", question);
                    return new QuestionResponse(
                        question,
                        "Errore durante l'elaborazione AI: " + err.getMessage(),
                        new ArrayList<>(),
                        0.0
                    );
                });
            processingUnis.add(singleQueryUni);
        }

        return Uni.join().all(processingUnis).andFailFast();
    }

    /**
     * Extracts questions from a Word table.
     */
    private void extractQuestionsFromTable(XWPFTable table, List<String> questions) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows == null || rows.size() < 2) return;

        // Detect question column index
        int questionColIndex = findQuestionColumnInTable(rows);

        int startRow = hasHeaderRow(rows, questionColIndex) ? 1 : 0;
        for (int r = startRow; r < rows.size(); r++) {
            XWPFTableRow row = rows.get(r);
            List<XWPFTableCell> cells = row.getTableCells();
            if (cells == null || cells.isEmpty()) continue;

            int colToRead = Math.min(questionColIndex, cells.size() - 1);
            String cellText = cells.get(colToRead).getText().trim();

            if (!cellText.isEmpty() && cellText.length() >= 5 && !isHeaderLabel(cellText)) {
                questions.add(cellText);
            }
        }
    }

    /**
     * Identifies which table column contains the questions based on header keywords or text length.
     */
    private int findQuestionColumnInTable(List<XWPFTableRow> rows) {
        if (rows.isEmpty()) return 0;

        XWPFTableRow headerRow = rows.get(0);
        List<XWPFTableCell> headerCells = headerRow.getTableCells();
        if (headerCells == null || headerCells.isEmpty()) return 0;

        for (int c = 0; c < headerCells.size(); c++) {
            String text = headerCells.get(c).getText().toLowerCase().trim();
            if (text.contains("domanda") || text.contains("question") || text.contains("requisito")
                || text.contains("control") || text.contains("security requirement") || text.contains("item")
                || text.contains("descrizione") || text.contains("description")) {
                return c;
            }
        }

        // If only 2 columns, column 0 is typically the question
        if (headerCells.size() == 2) {
            return 0;
        }

        // If 3+ columns, column 1 is usually the question description (col 0 being ID/Number)
        if (headerCells.size() >= 3) {
            return 1;
        }

        return 0;
    }

    private boolean hasHeaderRow(List<XWPFTableRow> rows, int questionCol) {
        if (rows.isEmpty()) return false;
        List<XWPFTableCell> firstRowCells = rows.get(0).getTableCells();
        if (firstRowCells == null || firstRowCells.isEmpty()) return false;
        int col = Math.min(questionCol, firstRowCells.size() - 1);
        String text = firstRowCells.get(col).getText().toLowerCase().trim();
        return isHeaderLabel(text);
    }

    private boolean isHeaderLabel(String text) {
        String lower = text.toLowerCase().trim();
        return lower.equals("domanda") || lower.equals("domande") || lower.equals("question")
            || lower.equals("questions") || lower.equals("requisito") || lower.equals("control")
            || lower.equals("description") || lower.equals("descrizione") || lower.equals("item")
            || lower.equals("#") || lower.equals("id") || lower.equals("n.");
    }

    /**
     * Extracts questions from paragraphs when no structured tables exist.
     */
    private void extractQuestionsFromParagraphs(List<XWPFParagraph> paragraphs, List<String> questions) {
        if (paragraphs == null) return;

        for (XWPFParagraph p : paragraphs) {
            String text = p.getText().trim();
            if (text.length() < 10) continue;

            // Match numbered questions or lines ending with a question mark
            if (text.endsWith("?") || NUMBERED_PATTERN.matcher(text).matches()) {
                questions.add(text);
            }
        }
    }

    /**
     * Exports reviewed and approved questionnaire responses into a formatted Word (.docx) document.
     */
    public byte[] exportToDocx(List<ExportRowRequest> rows) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 1. Document Title
            XWPFParagraph titlePara = doc.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.LEFT);
            titlePara.setSpacingAfter(100);

            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText("PassProof - Questionario di Sicurezza Compilato");
            titleRun.setBold(true);
            titleRun.setFontSize(18);
            titleRun.setFontFamily("Segoe UI");
            titleRun.setColor("4F46E5"); // Brand Indigo

            // 2. Subtitle / Metadata
            XWPFParagraph subtitlePara = doc.createParagraph();
            subtitlePara.setAlignment(ParagraphAlignment.LEFT);
            subtitlePara.setSpacingAfter(300);

            XWPFRun subtitleRun = subtitlePara.createRun();
            String dateFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            subtitleRun.setText("Data di esportazione: " + dateFormatted + "  |  Totale Domande: " + rows.size() + "  |  Verificato con PassProof RAG AI");
            subtitleRun.setFontSize(10);
            subtitleRun.setFontFamily("Segoe UI");
            subtitleRun.setColor("64748B"); // Slate 500

            // 3. Results Table
            XWPFTable table = doc.createTable(rows.size() + 1, 4);
            table.setWidth("100%");

            // Configure Header Row
            XWPFTableRow headerRow = table.getRow(0);
            headerRow.setRepeatHeader(true);
            String[] headers = {"#", "Domanda del Cliente", "Risposta Suggerita / Approvata", "Confidenza AI"};
            int[] colWidths = {600, 3800, 5000, 1400}; // relative units

            for (int i = 0; i < headers.length; i++) {
                XWPFTableCell cell = headerRow.getCell(i);
                cell.setWidth(String.valueOf(colWidths[i]));
                cell.setColor("0F172A"); // Dark slate background
                
                XWPFParagraph p = cell.getParagraphs().get(0);
                p.setSpacingBefore(120);
                p.setSpacingAfter(120);
                p.setAlignment(ParagraphAlignment.LEFT);

                XWPFRun r = p.createRun();
                r.setText(headers[i]);
                r.setBold(true);
                r.setFontSize(10);
                r.setFontFamily("Segoe UI");
                r.setColor("FFFFFF"); // White text
            }

            // Populate Table Rows
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                ExportRowRequest rowData = rows.get(rowIndex);
                XWPFTableRow tableRow = table.getRow(rowIndex + 1);

                // Alternating row background for clean readability
                String rowBgColor = (rowIndex % 2 == 1) ? "F8FAFC" : "FFFFFF";

                // Cell 0: Row Index
                setCellContent(tableRow.getCell(0), "#" + (rowIndex + 1), rowBgColor, true, "64748B", 9, colWidths[0]);

                // Cell 1: Question Text
                setCellContent(tableRow.getCell(1), rowData.getQuestion(), rowBgColor, true, "0F172A", 9, colWidths[1]);

                // Cell 2: Final Answer
                String answer = rowData.getAnswer() != null ? rowData.getAnswer() : "";
                setCellContent(tableRow.getCell(2), answer, rowBgColor, false, "1E293B", 9, colWidths[2]);

                // Cell 3: Confidence
                String confText = Math.round(rowData.getConfidence() * 100) + "%";
                if (rowData.getConfidence() >= 0.8) confText += " (Alta)";
                else if (rowData.getConfidence() >= 0.5) confText += " (Media)";
                else confText += " (Bassa)";
                setCellContent(tableRow.getCell(3), confText, rowBgColor, false, "4F46E5", 9, colWidths[3]);
            }

            // 4. Footer info note
            XWPFParagraph footerPara = doc.createParagraph();
            footerPara.setSpacingBefore(300);
            XWPFRun footerRun = footerPara.createRun();
            footerRun.setText("Documento generato automaticamente tramite PassProof (https://www.passproof.app). Tutti i dati sono protetti e riservati.");
            footerRun.setFontSize(8);
            footerRun.setFontFamily("Segoe UI");
            footerRun.setColor("94A3B8");
            footerRun.setItalic(true);

            doc.write(out);
            return out.toByteArray();
        }
    }

    private void setCellContent(XWPFTableCell cell, String text, String bgColor, boolean bold, String color, int fontSize, int width) {
        cell.setWidth(String.valueOf(width));
        if (bgColor != null && !bgColor.equals("FFFFFF")) {
            cell.setColor(bgColor);
        }

        XWPFParagraph p = cell.getParagraphs().get(0);
        p.setSpacingBefore(100);
        p.setSpacingAfter(100);

        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(bold);
        r.setColor(color);
        r.setFontSize(fontSize);
        r.setFontFamily("Segoe UI");
    }
}
