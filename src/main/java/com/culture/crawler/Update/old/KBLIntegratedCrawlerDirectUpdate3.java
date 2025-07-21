package com.culture.crawler.Update.old;

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
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

public class KBLIntegratedCrawlerDirectUpdate3 {

	public static void main(String[] args) {
	    try {
	        // 1. 경기일정 크롤링
	        String latestMatchDate = runMatchScheduleCrawlerAndInsertToDB(); 
	        
	        // 2. 관중수 엑셀 다운로드
	        Path excelFile = runCrowdExcelDownload();
	        
	        if (excelFile != null && latestMatchDate != null) {
	            // 3. 엑셀 기반 DB 업데이트 (수집 후 마지막 경기일 기준으로 업데이트)
	            updateCrowdFromExcel(excelFile, latestMatchDate); 
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}
	
	public static String runMatchScheduleCrawlerAndInsertToDB() throws Exception {
	    System.setProperty("webdriver.chrome.driver", "C:\\chromedriver\\chromedriver.exe");

	    ChromeOptions options = new ChromeOptions();
	    options.addArguments("--remote-allow-origins=*");
	    WebDriver driver = new ChromeDriver(options);
	    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

	    String latestMatchDate = "20230101";

	    try (Connection conn = DriverManager.getConnection(
	            "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul", "root", "1234")) {

	        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

	        try (PreparedStatement stmt = conn.prepareStatement(
	                "SELECT MAX(MATCH_DE) FROM colct_sports_match_info WHERE GRP_NM = 'KBL'")) {
	            ResultSet rs = stmt.executeQuery();
	            if (rs.next() && rs.getString(1) != null) {
	                latestMatchDate = rs.getString(1);
	            }
	            rs.close();
	        }

	        System.out.println("📌 KBL DB 내 마지막 경기일: " + latestMatchDate);

	        driver.get("https://www.kbl.or.kr/match/schedule?type=SCHEDULE");
	        driver.manage().window().maximize();
	        Thread.sleep(3000);

	        String[] currentMonthParts = driver.findElement(By.cssSelector(
	                "#root > main > div > div.contents > div.filter-wrap > div > ul > li:nth-child(2) > p"))
	                .getText().trim().split("\\.");
	        int currentYear = Integer.parseInt(currentMonthParts[0].trim());
	        int currentMonth = Integer.parseInt(currentMonthParts[1].trim());

	        LocalDate latestDate = LocalDate.parse(latestMatchDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
	        LocalDate startDate = LocalDate.of(currentYear, currentMonth, 1);
	        if (startDate.isBefore(latestDate.plusDays(1))) {
	            startDate = latestDate.plusDays(1);
	        }

	        List<Map<String, String>> parsedList = new ArrayList<>();

	        while (!startDate.isBefore(latestDate.minusMonths(1))) {
	            System.out.println("[INFO] 수집 중: " + startDate.getYear() + ". " + startDate.getMonthValue());

	            List<WebElement> gameLists = driver.findElements(By.cssSelector(".cont-box .game-schedule-list"));
	            for (WebElement gameList : gameLists) {
	                String matchDe = gameList.getAttribute("id").replace("game-", "").trim();
	                if (matchDe.compareTo(latestMatchDate) <= 0) continue;

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

	                    Map<String, String> row = new HashMap<>();
	                    row.put("matchDe", matchDe);
	                    row.put("baseYear", baseYear);
	                    row.put("baseMonth", baseMonth);
	                    row.put("baseDay", baseDay);
	                    row.put("gameType", gameType);
	                    row.put("stadium", stadium);
	                    row.put("homeTeam", homeTeam);
	                    row.put("awayTeam", awayTeam);
	                    parsedList.add(row);
	                }
	            }

	            startDate = startDate.minusMonths(1);
	            driver.findElement(By.cssSelector(".filter-wrap ul.date li:nth-child(1) button")).click();
	            Thread.sleep(2000);
	        }

	        // ✅ 첫 번째 테이블 처리
	        int totalKblSc = parsedList.size();
	        int insertedKblSc = 0;
	        try (
	            PreparedStatement insertStmtKblSc = conn.prepareStatement("""
	                INSERT INTO colct_sports_match_info_KBL_SC (
	                    MATCH_DE, LEA_NM, STDM_NM, HOME_TEAM_NM, AWAY_TEAM_NM
	                ) VALUES (?, ?, ?, ?, ?)
	            """)
	        ) {
	            conn.setAutoCommit(false);
	            for (Map<String, String> row : parsedList) {
	                insertStmtKblSc.setString(1, row.get("matchDe"));
	                insertStmtKblSc.setString(2, row.get("gameType"));
	                insertStmtKblSc.setString(3, row.get("stadium"));
	                insertStmtKblSc.setString(4, row.get("homeTeam"));
	                insertStmtKblSc.setString(5, row.get("awayTeam"));
	                insertStmtKblSc.addBatch();
	            }
	            insertedKblSc = insertStmtKblSc.executeBatch().length;
	            conn.commit();
	            insertLog("KBL 스포츠 관람", "KBL 경기일정 추가", "colct_sports_match_info_KBL_SC", "SUCCESS",
	                    totalKblSc, insertedKblSc, totalKblSc - insertedKblSc, "", today);
	            System.out.println("✅ KBL_SC 테이블 배치 성공");
	        } catch (SQLException e) {
	            conn.rollback();
	            insertLog("KBL 스포츠 관람", "KBL 경기일정 추가", "colct_sports_match_info_KBL_SC", "FAILED",
	                    totalKblSc, 0, totalKblSc, e.getMessage(), today);
	            System.err.println("❌ KBL_SC 배치 실패: " + e.getMessage());
	        }

	        // ✅ 두 번째 테이블 처리
	        int totalColct = parsedList.size();
	        int insertedColct = 0;
	        try (
	            PreparedStatement insertStmtColct = conn.prepareStatement("""
	                INSERT INTO colct_sports_match_info (
	                    MATCH_DE, BASE_YEAR, BASE_MT, BASE_DAY, GRP_NM, LEA_NM, HOME_TEAM_NM, AWAY_TEAM_NM,
	                    STDM_NM, SPORTS_VIEWNG_NMPR_CO, COLCT_DE, UPDT_DE
	                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	            """)
	        ) {
	            conn.setAutoCommit(false);
	            for (Map<String, String> row : parsedList) {
	                insertStmtColct.setString(1, row.get("matchDe"));
	                insertStmtColct.setString(2, row.get("baseYear"));
	                insertStmtColct.setString(3, row.get("baseMonth"));
	                insertStmtColct.setString(4, row.get("baseDay"));
	                insertStmtColct.setString(5, "KBL");
	                insertStmtColct.setString(6, row.get("gameType"));
	                insertStmtColct.setString(7, row.get("homeTeam"));
	                insertStmtColct.setString(8, row.get("awayTeam"));
	                insertStmtColct.setString(9, row.get("stadium"));
	                insertStmtColct.setNull(10, Types.DECIMAL);
	                insertStmtColct.setString(11, today);
	                insertStmtColct.setString(12, today);
	                insertStmtColct.addBatch();
	            }
	            insertedColct = insertStmtColct.executeBatch().length;
	            conn.commit();
	            insertLog("KBL 스포츠 관람", "KBL 경기일정 추가", "colct_sports_match_info", "SUCCESS",
	                    totalColct, insertedColct, totalColct - insertedColct, "", today);
	            System.out.println("✅ colct_sports_match_info 테이블 배치 성공");
	        } catch (SQLException e) {
	            conn.rollback();
	            insertLog("KBL 스포츠 관람", "KBL 경기일정 추가", "colct_sports_match_info", "FAILED",
	                    totalColct, 0, totalColct, e.getMessage(), today);
	            System.err.println("❌ colct_sports_match_info 배치 실패: " + e.getMessage());
	        }

	        System.out.println("✅ KBL 경기일정 DB 저장 시도 완료");
	        return latestMatchDate;

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
    
    public static void updateCrowdFromExcel(Path excelPath, String latestMatchDate) throws Exception {
        Workbook workbook = null;
        FileInputStream fis = null;

        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul", "root", "1234")) {

            fis = new FileInputStream(excelPath.toFile());
            workbook = new XSSFWorkbook(fis);

            Sheet sheet = workbook.getSheetAt(0);
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalDate latestDate = LocalDate.parse(latestMatchDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

            List<Map<String, String>> dataList = new ArrayList<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String season = getCellString(row, 0);
                String homeRaw = getCellString(row, 1);
                String leagueName = getCellString(row, 2);
                String stadiumName = getCellString(row, 5);
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
                if (matchDe.compareTo(latestMatchDate) <= 0) continue;

                String cleanedCrowd = rawCrowd.replace(",", "").replace("명", "").trim();
                if (cleanedCrowd.isEmpty()) continue;

                String homeTeam = normalizeTeamName(extractSecondWordOrOriginal(homeRaw));

                Map<String, String> map = new HashMap<>();
                map.put("matchDe", matchDe);
                map.put("leagueName", leagueName);
                map.put("stadiumName", stadiumName);
                map.put("homeTeam", homeTeam);
                map.put("crowd", cleanedCrowd);
                dataList.add(map);
            }

            int insertedCount = 0;
            int updatedCount = 0;
            int notUpdatedCount = 0;

            // ✅ colct_sports_match_info_KBL_CD 테이블: INSERT
            try (PreparedStatement insertStmt = conn.prepareStatement("""
                    INSERT INTO colct_sports_match_info_KBL_CD (
                        MATCH_DE, LEA_NM, STDM_NM, HOME_TEAM_NM, SPORTS_VIEWNG_NMPR_CO
                    ) VALUES (?, ?, ?, ?, ?)
                """)) {
                conn.setAutoCommit(false);
                for (Map<String, String> row : dataList) {
                    insertStmt.setString(1, row.get("matchDe"));
                    insertStmt.setString(2, row.get("leagueName"));
                    insertStmt.setString(3, row.get("stadiumName"));
                    insertStmt.setString(4, row.get("homeTeam"));
                    insertStmt.setBigDecimal(5, new BigDecimal(row.get("crowd") + ".00000"));
                    insertStmt.addBatch();
                }
                insertedCount = insertStmt.executeBatch().length;
                conn.commit();

                insertLog("KBL 스포츠 관람", "KBL 관중수 추가", "colct_sports_match_info_KBL_CD", "SUCCESS",
                        dataList.size(), insertedCount, dataList.size() - insertedCount, "", today);
                System.out.println("✅ KBL_CD 테이블 배치 완료: " + insertedCount + "건");

            } catch (SQLException e) {
                conn.rollback();
                insertLog("KBL 스포츠 관람", "KBL 관중수 추가", "colct_sports_match_info_KBL_CD", "FAILED",
                        dataList.size(), 0, dataList.size(), e.getMessage(), today);
                System.err.println("❌ KBL_CD 배치 실패: " + e.getMessage());
            }


         // ✅ colct_sports_match_info 테이블: UPDATE
            try (PreparedStatement updateStmt = conn.prepareStatement("""
                    UPDATE colct_sports_match_info
                    SET SPORTS_VIEWNG_NMPR_CO = ?, UPDT_DE = ?
                    WHERE MATCH_DE = ? AND GRP_NM = ? AND HOME_TEAM_NM = ?
                """)) {

                conn.setAutoCommit(false);

                for (Map<String, String> row : dataList) {
                    updateStmt.setBigDecimal(1, new BigDecimal(row.get("crowd") + ".00000"));
                    updateStmt.setString(2, today);
                    updateStmt.setString(3, row.get("matchDe"));
                    updateStmt.setString(4, "KBL");
                    updateStmt.setString(5, row.get("homeTeam"));
                    updateStmt.addBatch();
                }

                int[] results = updateStmt.executeBatch();
                conn.commit();

                int updatedCount1 = 0;
                int notUpdatedCount1 = 0;

                for (int result : results) {
                    if (result > 0) updatedCount1++;
                    else notUpdatedCount1++;
                }

                String updateStatus;
                String errorMsgForLog;

                if (updatedCount1 > 0) {
                    updateStatus = "SUCCESS";
                    errorMsgForLog = "";
                } else {
                    updateStatus = "FAILED";
                    errorMsgForLog = "업데이트된 행이 없습니다.";
                }

                insertLog("KBL 스포츠 관람", "KBL 관중수 추가", "colct_sports_match_info", updateStatus,
                        dataList.size(), 0, updatedCount1, errorMsgForLog, today);

                if ("FAILED".equals(updateStatus)) {
                    System.err.println("❌ colct_sports_match_info 업데이트 전부 실패");
                } else {
                    System.out.println("✅ colct_sports_match_info 업데이트 완료: " + updatedCount1 + "건");
                }
                System.out.println("❌ 업데이트 실패 건수: " + notUpdatedCount1 + "건");

            } catch (SQLException e) {
                conn.rollback();
                insertLog("KBL 스포츠 관람", "KBL 관중수 추가", "colct_sports_match_info", "FAILED",
                        dataList.size(), 0, 0, e.getMessage(), today);
                System.err.println("❌ colct_sports_match_info 배치 실패: " + e.getMessage());
            }



            // ✅ 추가된 경기 수 확인
            int addedGamesCount = 0;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM colct_sports_match_info WHERE MATCH_DE > ? AND GRP_NM = 'KBL'")) {
                stmt.setString(1, latestMatchDate);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    addedGamesCount = rs.getInt(1);
                }
                rs.close();
            }
            System.out.println("🎯 DB 내 마지막 경기일 이후 추가된 경기 수: " + addedGamesCount + "건");

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

    // 로그 삽입 함수
    public static void insertLog(
            String groupNm, String jobNm, String tableNm, String stateCd,
            int collectedCount, int insertedCount, int updatedCount, String errorMsg, String today
    ) {
        // 로그 출력: 삽입하기 전의 값 확인
        System.out.println("📌 로그 삽입 직전 - 그룹명: " + groupNm);
        System.out.println("📌 작업명: " + jobNm);
        System.out.println("📌 테이블명: " + tableNm);
        System.out.println("📌 상태 코드: " + stateCd);
        System.out.println("📌 수집된 데이터 개수 (collectedCount): " + collectedCount);
        System.out.println("📌 삽입된 데이터 개수 (insertedCount): " + insertedCount);
        System.out.println("📌 업데이트된 데이터 개수 (updatedCount): " + updatedCount);
        System.out.println("📌 에러 메시지 (errorMsg): " + errorMsg);

        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul",
                "root", "1234"
        )) {
            // 로그 테이블에 데이터 삽입
            String insertLogSql = "INSERT INTO colct_schd_log (" +
                    "COLCT_SCHD_GROUP_NM, COLCT_SCHD_JOB_NM, COLCT_SCHD_TABLE_NM, " +
                    "COLCT_STATE_CD, COLCT_CO, CRTN_CO, UPDT_CO, ERROR_MSG, COLCT_DT) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

            PreparedStatement pstmtLog = conn.prepareStatement(insertLogSql);
            pstmtLog.setString(1, groupNm);  // 예: "스포츠 관람"
            pstmtLog.setString(2, jobNm);    // 예: "스포츠 지역별 관중 추가"
            pstmtLog.setString(3, tableNm);  // 예: "sports_시도별관중"
            pstmtLog.setString(4, stateCd);  // 예: "SUCCESS", "FAILED"
            pstmtLog.setInt(5, collectedCount);  // 수집된 데이터 개수
            pstmtLog.setInt(6, insertedCount);  // 삽입된 데이터 개수
            pstmtLog.setInt(7, updatedCount);  // 업데이트된 데이터 개수
            pstmtLog.setString(8, errorMsg);   // 에러 메시지, 실패 시만 채움

            int result = pstmtLog.executeUpdate();
            if (result > 0) {
                System.out.println("✅ 로그 삽입 성공");
            } else {
                System.err.println("❌ 로그 삽입 실패: 테이블에 데이터가 삽입되지 않음.");
            }
            pstmtLog.close();
        } catch (SQLException e) {
            System.err.println("❌ 로그 삽입 실패: " + e.getMessage());
            System.err.println("    SQLState: " + e.getSQLState());
            System.err.println("    ErrorCode: " + e.getErrorCode());
        }
    }
}
