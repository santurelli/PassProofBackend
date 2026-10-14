package com.cyberpass.backend.service;

import com.cyberpass.backend.dto.ExportRowRequest;
import com.cyberpass.backend.dto.QuestionRequest;
import com.cyberpass.backend.dto.QuestionResponse;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jboss.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Service to process Excel questionnaires.
 * Reads questions from spreadsheet formats and compiles final reports using Apache POI.
 */
@ApplicationScoped
public class ExcelQuestionnaireService {

    private static final Logger LOG = Logger.getLogger(ExcelQuestionnaireService.class);

    @Inject
    QuestionnaireProcessingService questionnaireProcessingService;

    /**
     * Parses the Excel file from the temporary path, processes all questions through RAG,
     * and returns the list of responses.
     */
    public Uni<List<QuestionResponse>> parseAndProcessExcel(Path excelPath) {
        List<String> questions = new ArrayList<>();
        
        try (InputStream is = new FileInputStream(excelPath.toFile());
             Workbook workbook = WorkbookFactory.create(is)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0) {
                return Uni.createFrom().failure(new IllegalArgumentException("The Excel sheet is empty."));
            }

            // Identify the column containing questions
            int questionColIndex = findQuestionColumn(sheet);
            LOG.infof("Using column index %d for questionnaire parsing.", questionColIndex);

            // Read questions (skipping the first header row if it contains headers)
            int startRow = hasHeaderRow(sheet, questionColIndex) ? 1 : 0;
            for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Cell cell = row.getCell(questionColIndex);
                if (cell == null) continue;

                String cellValue = getCellValueAsString(cell).trim();
                if (!cellValue.isEmpty()) {
                    questions.add(cellValue);
                }
            }

        } catch (IOException e) {
            LOG.error("Failed to read uploaded Excel file", e);
            return Uni.createFrom().failure(e);
        }

        if (questions.isEmpty()) {
            return Uni.createFrom().failure(new IllegalArgumentException("No text questions could be parsed from the Excel file."));
        }

        LOG.infof("Parsed %d questions from spreadsheet. Initializing RAG pipeline.", questions.size());

        // Process all questions reactively and collect them in order
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
     * Compiles final reviewed rows back into a new styled Excel sheet.
     */
    public byte[] exportToExcel(List<ExportRowRequest> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet("Risposte CyberPass");

            // Fonts and styling
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 11);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.LEFT);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Double border styling for headers
            headerStyle.setBorderBottom(BorderStyle.DOUBLE);
            headerStyle.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setWrapText(true);
            dataStyle.setVerticalAlignment(VerticalAlignment.TOP);

            CellStyle pctStyle = workbook.createCellStyle();
            pctStyle.setDataFormat(workbook.createDataFormat().getFormat("0.0%"));
            pctStyle.setVerticalAlignment(VerticalAlignment.TOP);
            pctStyle.setAlignment(HorizontalAlignment.RIGHT);

            // Create header row
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(24);
            
            String[] headers = {"Domanda", "Risposta Suggerita / Approvata", "Confidenza AI"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Populate rows
            int rowNum = 1;
            for (ExportRowRequest rowData : rows) {
                Row row = sheet.createRow(rowNum++);
                
                Cell c0 = row.createCell(0);
                c0.setCellValue(rowData.getQuestion());
                c0.setCellStyle(dataStyle);

                Cell c1 = row.createCell(1);
                c1.setCellValue(rowData.getAnswer());
                c1.setCellStyle(dataStyle);

                Cell c2 = row.createCell(2);
                c2.setCellValue(rowData.getConfidence());
                c2.setCellStyle(pctStyle);
            }

            // Auto-fit column widths
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // Apply a maximum width constraint to prevent overly stretched cells
                int colWidth = sheet.getColumnWidth(i);
                if (i < 2 && colWidth > 15000) {
                    sheet.setColumnWidth(i, 15000);
                }
            }

            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    private int findQuestionColumn(Sheet sheet) {
        Row firstRow = sheet.getRow(sheet.getFirstRowNum());
        if (firstRow != null) {
            for (int c = 0; c < firstRow.getLastCellNum(); c++) {
                Cell cell = firstRow.getCell(c);
                if (cell == null) continue;
                String cellText = getCellValueAsString(cell).toLowerCase();
                if (cellText.contains("domanda") || cellText.contains("question") || cellText.contains("testo")) {
                    return c;
                }
            }
        }
        
        // Fallback: search row 0 and row 1 for any non-empty cell as a heuristic
        return 0;
    }

    private boolean hasHeaderRow(Sheet sheet, int colIndex) {
        Row firstRow = sheet.getRow(sheet.getFirstRowNum());
        if (firstRow == null) return false;
        
        Cell cell = firstRow.getCell(colIndex);
        if (cell == null) return false;

        String val = getCellValueAsString(cell).toLowerCase();
        // If the first cell matches header text, we treat row 0 as a header
        return val.contains("domanda") || val.contains("question") || val.contains("richiesta") || val.contains("testo");
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }
}
