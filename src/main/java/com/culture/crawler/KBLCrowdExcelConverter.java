package com.culture.crawler;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class KBLCrowdExcelConverter {
    public static void main(String[] args) {
        String excelFileName = "관중현황.xlsx"; // 다운로드된 엑셀파일명
        String csvFileName = "kbl_matchinfo_2024.csv";
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(excelFileName));
             PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(csvFileName, false)))) {

            Sheet sheet = workbook.getSheetAt(0);
            writer.println("MATCH_DE,BASE_YEAR,BASE_MT,BASE_DAY,GRP_NM,LEA_NM,HOME_TEAM_NM,AWAY_TEAM_NM,STDM_NM,SPORTS_VIEWNG_NMPR_CO,COLCT_DE,UPDT_DE");

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String dateStr = getCellString(row, 0).replace("/", "-");
                LocalDate matchDate;
                try {
                    matchDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                } catch (Exception e) {
                    continue;
                }

                String matchDe = matchDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                String baseYear = matchDate.format(DateTimeFormatter.ofPattern("yyyy"));
                String baseMonth = matchDate.format(DateTimeFormatter.ofPattern("MM"));
                String baseDay = matchDate.format(DateTimeFormatter.ofPattern("dd"));

                String team = getCellString(row, 1);       // 홈팀
                String stadium = getCellString(row, 3);    // 경기장
                String crowdRaw = getCellString(row, 5).replace(",", "").replace("명", "");
                if (crowdRaw.isEmpty()) crowdRaw = "0";
                String crowd = crowdRaw + ".00000";

                String line = String.join(",", matchDe, baseYear, baseMonth, baseDay,
                        "프로스포츠", "KBL", team, "", stadium, crowd, today, today);
                writer.println(line);
            }

            System.out.println("✅ CSV 저장 완료: " + csvFileName);

        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Files.deleteIfExists(Paths.get(excelFileName));
            System.out.println("🗑️ 엑셀 파일 삭제 완료: " + excelFileName);
        } catch (IOException e) {
            System.err.println("❌ 엑셀 삭제 실패: " + e.getMessage());
        }
    }

    private static String getCellString(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> "";
        };
    }
}
