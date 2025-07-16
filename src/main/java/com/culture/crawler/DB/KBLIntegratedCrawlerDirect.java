package com.culture.crawler.DB;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

public class KBLIntegratedCrawlerDirect {

    public static void main(String[] args) {
        try {
        	runMatchScheduleCrawlerAndInsertToDB(); // 1. 경기일정 크롤링
            Path excelFile = runCrowdExcelDownload(); // 2. 관중수 엑셀 다운로드
            if (excelFile != null) updateCrowdFromExcel(excelFile); // 3. 엑셀 기반 DB 업데이트
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void runMatchScheduleCrawlerAndInsertToDB() throws Exception {
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver\\chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul", "root", "1234")) {

            conn.setAutoCommit(false);
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            PreparedStatement insertStmt = conn.prepareStatement("""
                INSERT INTO colct_sports_match_info (
                    MATCH_DE, BASE_YEAR, BASE_MT, BASE_DAY,
                    GRP_NM, LEA_NM, HOME_TEAM_NM, AWAY_TEAM_NM,
                    STDM_NM, SPORTS_VIEWNG_NMPR_CO, COLCT_DE, UPDT_DE
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """);

            PreparedStatement checkStmt = conn.prepareStatement("""
                SELECT COUNT(*) FROM colct_sports_match_info
                WHERE MATCH_DE = ? AND GRP_NM = ? AND HOME_TEAM_NM = ? AND AWAY_TEAM_NM = ?
            """);

            driver.get("https://www.kbl.or.kr/match/schedule?type=SCHEDULE");
            driver.manage().window().maximize();
            Thread.sleep(3000);

            int startYear = 2023;
            int startMonth = 1;
            int endYear = 2025;
            int endMonth = 7;

            List<String> targetMonths = new ArrayList<>();
            for (int year = endYear; year >= startYear; year--) {
                int from = (year == endYear) ? endMonth : 12;
                int to = (year == startYear) ? startMonth : 1;
                for (int month = from; month >= to; month--) {
                    targetMonths.add(String.format("%d. %02d", year, month));
                }
            }

            for (String targetMonth : targetMonths) {
                while (true) {
                    String currentMonth = driver.findElement(By.cssSelector(".filter-wrap ul.date li:nth-child(2) p"))
                            .getText().trim();
                    if (currentMonth.equals(targetMonth)) break;

                    driver.findElement(By.cssSelector(".filter-wrap ul.date li:nth-child(1) button")).click();
                    Thread.sleep(2000);
                }

                System.out.println("[INFO] 수집 중: " + targetMonth);

                List<WebElement> gameLists = driver.findElements(By.cssSelector(".cont-box .game-schedule-list"));
                for (WebElement gameList : gameLists) {
                    String matchDe = gameList.getAttribute("id").replace("game-", "").trim();
                    String baseYear = matchDe.substring(0, 4);
                    String baseMonth = matchDe.substring(4, 6);
                    String baseDay = matchDe.substring(6, 8);

                    List<WebElement> items = gameList.findElements(By.cssSelector("ul > li"));
                    for (WebElement item : items) {
                        if (item.findElements(By.cssSelector("div.sub")).isEmpty()) continue;
                        WebElement desc = item.findElement(By.cssSelector("div.sub > div.desc"));
                        String gameType = desc.findElement(By.cssSelector("span.label")).getText().trim();
                        String stadium = desc.findElements(By.cssSelector("ul > li")).get(1).getText().trim();

                        List<WebElement> teams = item.findElements(By.cssSelector("div.info ul.versus > li"));
                        if (teams.size() != 2) continue;

                        String homeTeam = normalizeTeamName(teams.get(0).findElements(By.tagName("p")).get(0).getText().trim());
                        String awayTeam = normalizeTeamName(teams.get(1).findElements(By.tagName("p")).get(0).getText().trim());



//                        // 중복 검사
//                        checkStmt.setString(1, matchDe);
//                        checkStmt.setString(2, "KBL");
//                        checkStmt.setString(3, homeTeam);
//                        checkStmt.setString(4, awayTeam);
//
//                        ResultSet rs = checkStmt.executeQuery();
//                        rs.next();
//                        if (rs.getInt(1) > 0) {
//                            System.out.println("⏩ 중복 스킵: " + matchDe + " " + homeTeam + " vs " + awayTeam);
//                            rs.close();
//                            continue;
//                        }
//                        rs.close();

                        insertStmt.setString(1, matchDe);
                        insertStmt.setString(2, baseYear);
                        insertStmt.setString(3, baseMonth);
                        insertStmt.setString(4, baseDay);
                        insertStmt.setString(5, "KBL");
                        insertStmt.setString(6, gameType);
                        insertStmt.setString(7, homeTeam);
                        insertStmt.setString(8, awayTeam);
                        insertStmt.setString(9, stadium);
                        insertStmt.setNull(10, Types.DECIMAL); // 관중수는 NULL
                        insertStmt.setString(11, today); // COLCT_DE
                        insertStmt.setString(12, today); // UPDT_DE

                        insertStmt.addBatch();
                    }
                }
            }

            insertStmt.executeBatch();
            conn.commit();
            System.out.println("✅ KBL 경기일정 DB 저장 완료");

        } finally {
            driver.quit();
        }
    }

    public static Path runCrowdExcelDownload() throws Exception {
        // ✅ 임시 다운로드 폴더 생성
        String baseDir = System.getProperty("user.dir");
        String timeTag = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        String tempFolderPath = baseDir + File.separator + "kbl_temp_" + timeTag;
        Files.createDirectories(Paths.get(tempFolderPath));

        // ✅ 다운로드 경로 설정
        HashMap<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", tempFolderPath);
        prefs.put("download.prompt_for_download", false);
        prefs.put("profile.default_content_setting_values.automatic_downloads", 1);
        prefs.put("safebrowsing.enabled", true);

        // ✅ 크롬 옵션 설정
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("prefs", prefs);
        options.addArguments("--remote-allow-origins=*");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(15));

//        // ✅ 생성된 폴더 자동 열기
//        try {
//            System.out.println("📁 생성된 임시폴더: " + tempFolderPath);
//            new ProcessBuilder("cmd", "/c", "start", "", "\"" + tempFolderPath + "\"").start();
//        } catch (Exception e) {
//            System.out.println("❌ 폴더 자동 열기 실패: " + e.getMessage());
//        }

        try {
            // ✅ 사이트 진입 및 메뉴 선택
            driver.get("https://kbl.or.kr/record/crowd");
            driver.manage().window().maximize();

            new Select(wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("#s2iZone > div > ul > li > select")))).selectByValue("team");
            Thread.sleep(1000);
            new Select(wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("#s2iZone > div > ul > li:nth-child(2) > select")))).selectByValue("00");
            Thread.sleep(1500);

            WebElement excelButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("#s2iZone > div > div:nth-child(5) > div.mid-title > div > button")));
            excelButton.click();
            System.out.println("📥 엑셀 다운로드 클릭 완료");

            // ✅ 엑셀 다운로드 대기
            Path latestXlsx = null;
            for (int i = 0; i < 20; i++) {
                latestXlsx = getLatestXlsxFile(tempFolderPath);
                if (latestXlsx != null) break;
                Thread.sleep(500);
            }

            if (latestXlsx == null || !waitForDownloadComplete(latestXlsx, 10)) {
                System.err.println("❌ 엑셀 다운로드 실패 또는 완료되지 않음");
                return null;
            }

            System.out.println("✅ 엑셀 다운로드 완료: " + latestXlsx);
            return latestXlsx;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            driver.quit();
        }
    }

    // 가장 최신 엑셀 파일 찾기
    private static Path getLatestXlsxFile(String dir) {
        try (Stream<Path> files = Files.list(Paths.get(dir))) {
            return files
                    .filter(p -> p.getFileName().toString().startsWith("팀 관중현황"))
                    .filter(p -> p.toString().endsWith(".xlsx"))
                    .filter(p -> !p.toString().endsWith(".crdownload"))
                    .max(Comparator.comparing(p -> {
                        try {
                            return Files.getLastModifiedTime(p);
                        } catch (IOException e) {
                            return FileTime.fromMillis(0);
                        }
                    }))
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean waitForDownloadComplete(Path file, int maxWaitSeconds) {
        try {
            long previousSize = -1;
            for (int i = 0; i < maxWaitSeconds * 2; i++) {
                if (!Files.exists(file)) return false;
                long currentSize = Files.size(file);
                if (currentSize > 0 && currentSize == previousSize) {
                    return true; // 크기 변화 없음 → 완료
                }
                previousSize = currentSize;
                Thread.sleep(500);
            }
        } catch (Exception e) {
            System.out.println("❌ waitForDownloadComplete 실패: " + e.getMessage());
        }
        return false;
    }
    public static void updateCrowdFromExcel(Path excelPath) throws Exception {
        Workbook workbook = null;
        FileInputStream fis = null;

        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul", "root", "1234")) {

            // 엑셀 파일을 먼저 FileInputStream으로 열고 Workbook으로 로드
            fis = new FileInputStream(excelPath.toFile());
            workbook = new XSSFWorkbook(fis);

            Sheet sheet = workbook.getSheetAt(0);
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            int totalDbKblCount = 0;
            try (PreparedStatement countStmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM colct_sports_match_info WHERE GRP_NM = 'KBL'")) {
                ResultSet rs = countStmt.executeQuery();
                if (rs.next()) totalDbKblCount = rs.getInt(1);
                rs.close();
            }
            System.out.println("📊 DB 내 전체 KBL 경기 수: " + totalDbKblCount + "건");

            PreparedStatement updateStmt = conn.prepareStatement("""
                UPDATE colct_sports_match_info
                SET SPORTS_VIEWNG_NMPR_CO = ?, UPDT_DE = ?
                WHERE MATCH_DE = ? AND GRP_NM = ? AND HOME_TEAM_NM = ?
            """);

            int updatedCount = 0;

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String season = getCellString(row, 0);
                String homeRaw = getCellString(row, 1);
                String rawDate = getCellString(row, 6);
                String rawCrowd = getCellString(row, 7);

                if (!rawDate.matches("\\d{1,2}\\.\\d{1,2}")) continue;

                String[] parts = rawDate.split("\\.");
                int mm = Integer.parseInt(parts[0]);
                int dd = Integer.parseInt(parts[1]);

                int startYear = Integer.parseInt(season.split("-")[0].trim());
                int endYear = Integer.parseInt(season.split("-")[1].trim());
                int year = (mm <= 6) ? endYear : startYear;

                String matchDe = LocalDate.of(year, mm, dd).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                String cleanedCrowd = rawCrowd.replace(",", "").replace("명", "").trim();
                if (cleanedCrowd.isEmpty()) continue;

                String homeTeam = normalizeTeamName(extractSecondWordOrOriginal(homeRaw));
                String grpNm = "KBL";

                updateStmt.setBigDecimal(1, new BigDecimal(cleanedCrowd + ".00000"));
                updateStmt.setString(2, today);
                updateStmt.setString(3, matchDe);
                updateStmt.setString(4, grpNm);
                updateStmt.setString(5, homeTeam);

                int updated = updateStmt.executeUpdate();
                if (updated > 0) {
                    updatedCount++;
                    System.out.println("✅ 업데이트됨: " + matchDe + " / " + homeTeam + " / " + cleanedCrowd);
                } else {
                    System.out.println("❌ 업데이트 실패: " + matchDe + " / " + homeTeam);
                }
            }

            updateStmt.close();
            System.out.println("🎉 관중수 업데이트 완료");
            System.out.println("🎯 DB 내 KBL 총 경기 수: " + totalDbKblCount + "건");
            System.out.println("🎯 실제 업데이트된 경기 수: " + updatedCount + "건");

        } finally {
            if (workbook != null) workbook.close();
            if (fis != null) fis.close();

            try {
                Files.deleteIfExists(excelPath);
                Files.walk(excelPath.getParent())
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            System.out.println("❌ 삭제 실패: " + path + " (" + e.getMessage() + ")");
                        }
                    });
                System.out.println("🧹 임시 폴더 및 파일 삭제 완료");
            } catch (IOException e) {
                System.out.println("❌ 전체 삭제 실패: " + e.getMessage());
            }
        }
    }



    // ✅ 관중수 파일용: 두 번째 단어 추출 (없으면 그대로)
    private static String extractSecondWordOrOriginal(String teamName) {
        String[] parts = teamName.trim().split("\\s+");
        return parts.length >= 2 ? parts[1] : parts[0];
    }
    
    private static String getCellString(Row row, int col) {
        if (row == null || row.getCell(col) == null) return "";
        Cell cell = row.getCell(col);
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell) ?
                    cell.getLocalDateTimeCellValue().toLocalDate().toString() :
                    String.format("%.2f", cell.getNumericCellValue());
            default -> "";
        };
    }

    private static String normalizeTeamName(String teamName) {
        teamName = teamName.trim();
        if (teamName.contains("정관장")) return "KGC";
        if (teamName.contains("고양 캐롯")) return "캐롯";
        return teamName;
    }

    private static String extractSecond(String name) {
        String[] parts = name.split("\\s+");
        return parts.length >= 2 ? parts[1] : parts[0];
    }
}
