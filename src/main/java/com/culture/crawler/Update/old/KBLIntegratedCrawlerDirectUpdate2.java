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
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

public class KBLIntegratedCrawlerDirectUpdate2 {

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

	    String latestMatchDate = "20230101";  // Default value

	    try (Connection conn = DriverManager.getConnection(
	            "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul", "root", "1234")) {

	        conn.setAutoCommit(false);
	        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

	        // DB에서 마지막 경기 날짜 조회
	        try (PreparedStatement stmt = conn.prepareStatement(
	                "SELECT MAX(MATCH_DE) FROM colct_sports_match_info WHERE GRP_NM = 'KBL'")) {
	            ResultSet rs = stmt.executeQuery();
	            if (rs.next() && rs.getString(1) != null) {
	                latestMatchDate = rs.getString(1);
	            }
	            rs.close();
	        }
	        System.out.println("📌 KBL DB 내 마지막 경기일: " + latestMatchDate);

	        // 두 개의 Insert Statement 준비
	        PreparedStatement insertStmtKblSc = conn.prepareStatement(""" 
	            INSERT INTO colct_sports_match_info_KBL_SC (
	                MATCH_DE, LEA_NM, STDM_NM, HOME_TEAM_NM, AWAY_TEAM_NM
	            ) VALUES (?, ?, ?, ?, ?)
	        """);

	        PreparedStatement insertStmtColctSportsMatchInfo = conn.prepareStatement(""" 
	            INSERT INTO colct_sports_match_info (
	                MATCH_DE, BASE_YEAR, BASE_MT, BASE_DAY, GRP_NM, LEA_NM, HOME_TEAM_NM, AWAY_TEAM_NM,
	                STDM_NM, SPORTS_VIEWNG_NMPR_CO, COLCT_DE, UPDT_DE
	            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	        """);

	        driver.get("https://www.kbl.or.kr/match/schedule?type=SCHEDULE");
	        driver.manage().window().maximize();
	        Thread.sleep(3000);

	        // 페이지에서 연도와 월을 정확히 추출
	        String currentMonthText = driver.findElement(By.cssSelector("#root > main > div > div.contents > div.filter-wrap > div > ul > li:nth-child(2) > p")).getText().trim();
	        String[] currentMonthParts = currentMonthText.split("\\.");
	        int currentYear = Integer.parseInt(currentMonthParts[0].trim());
	        int currentMonthInt = Integer.parseInt(currentMonthParts[1].trim());

	        System.out.println("[INFO] 현재 페이지 월: " + currentYear + "." + currentMonthInt);

	        // DB 마지막 경기 날짜 기준으로 시작
	        LocalDate latestDate = LocalDate.parse(latestMatchDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
	        LocalDate currentPageMonth = LocalDate.of(currentYear, currentMonthInt, 1); // 현재 페이지의 첫째 날

	        // 최신 달부터 시작하여 그 이전 달로 역순으로 수집
	        System.out.println("[INFO] 최신 달부터 수집 시작");

	        // 수집을 시작할 달을 결정
	        LocalDate startDate = currentPageMonth;
	        if (startDate.isBefore(latestDate)) {
	            startDate = latestDate;
	        }

	        // 수집을 시작하는 날짜를 최신 경기 날짜 이후로 설정
	        if (startDate.isBefore(latestDate.plusDays(1))) {
	            startDate = latestDate.plusDays(1);  // 4월 5일 이후부터 수집
	        }

	        // 월별로 역순으로 수집 시작
	        while (!startDate.isBefore(latestDate.minusMonths(1))) {
	            System.out.println("[INFO] 수집 중: " + startDate.getYear() + ". " + startDate.getMonthValue());

	            // 게임 리스트 크롤링
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

	                    // 첫 번째 테이블에 데이터 삽입 (colct_sports_match_info_KBL_SC)
	                    insertStmtKblSc.setString(1, matchDe);
	                    insertStmtKblSc.setString(2, gameType);
	                    insertStmtKblSc.setString(3, stadium);
	                    insertStmtKblSc.setString(4, homeTeam);
	                    insertStmtKblSc.setString(5, awayTeam);
	                    insertStmtKblSc.addBatch();

	                    // 두 번째 테이블에 데이터 삽입 (colct_sports_match_info)
	                    insertStmtColctSportsMatchInfo.setString(1, matchDe);
	                    insertStmtColctSportsMatchInfo.setString(2, baseYear);
	                    insertStmtColctSportsMatchInfo.setString(3, baseMonth);
	                    insertStmtColctSportsMatchInfo.setString(4, baseDay);
	                    insertStmtColctSportsMatchInfo.setString(5, "KBL");
	                    insertStmtColctSportsMatchInfo.setString(6, gameType);
	                    insertStmtColctSportsMatchInfo.setString(7, homeTeam);
	                    insertStmtColctSportsMatchInfo.setString(8, awayTeam);
	                    insertStmtColctSportsMatchInfo.setString(9, stadium);
	                    insertStmtColctSportsMatchInfo.setNull(10, Types.DECIMAL); // 관중수는 NULL
	                    insertStmtColctSportsMatchInfo.setString(11, today); // COLCT_DE
	                    insertStmtColctSportsMatchInfo.setString(12, today); // UPDT_DE
	                    insertStmtColctSportsMatchInfo.addBatch();
	                }
	            }

	            // 이전 달로 이동
	            startDate = startDate.minusMonths(1);

	            // 이전 달로 가기 위해 "이전 달" 버튼 클릭
	            driver.findElement(By.cssSelector(".filter-wrap ul.date li:nth-child(1) button")).click(); // 이전 달 버튼 클릭
	            Thread.sleep(2000);  // 페이지 로딩 대기
	        }

	        // 두 테이블에 배치된 데이터 실행
	        insertStmtKblSc.executeBatch();
	        insertStmtColctSportsMatchInfo.executeBatch();
	        conn.commit();
	        System.out.println("✅ KBL 경기일정 DB 저장 완료");

	        return latestMatchDate; // 수집된 최신 날짜 반환

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

            // 엑셀 파일을 먼저 FileInputStream으로 열고 Workbook으로 로드
            fis = new FileInputStream(excelPath.toFile());
            workbook = new XSSFWorkbook(fis);

            Sheet sheet = workbook.getSheetAt(0);
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            // 해당 날짜 이후의 데이터만 업데이트
            PreparedStatement insertStmtKblCd = conn.prepareStatement(""" 
                INSERT INTO colct_sports_match_info_KBL_CD (
                    MATCH_DE, LEA_NM, STDM_NM, HOME_TEAM_NM, SPORTS_VIEWNG_NMPR_CO
                ) VALUES (?, ?, ?, ?, ?)
            """);

            PreparedStatement updateStmt = conn.prepareStatement(""" 
                UPDATE colct_sports_match_info
                SET SPORTS_VIEWNG_NMPR_CO = ?, UPDT_DE = ?
                WHERE MATCH_DE = ? AND GRP_NM = ? AND HOME_TEAM_NM = ?
            """);

            int updatedCount = 0;
            int notUpdatedCount = 0;  // 업데이트되지 않은 경기 수

            // 기준일 이후의 데이터만 업데이트
            LocalDate latestDate = LocalDate.parse(latestMatchDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

            // 엑셀 파일을 한 행씩 순차적으로 읽어들임
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String season = getCellString(row, 0);
                String homeRaw = getCellString(row, 1);
                String leagueName = getCellString(row, 2); // 예시: 리그 이름 (엑셀에서 읽어온 값)
                String stadiumName = getCellString(row, 5); // 예시: 경기장 이름 (엑셀에서 읽어온 값)
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

                // 기준일 이후의 데이터만 처리
                if (matchDe.compareTo(latestMatchDate) <= 0) continue;

                // 먼저 colct_sports_match_info_KBL_CD 테이블에 데이터 삽입
                insertStmtKblCd.setString(1, matchDe);
                insertStmtKblCd.setString(2, leagueName); // 엑셀에서 읽어온 리그 이름 사용
                insertStmtKblCd.setString(3, stadiumName); // 엑셀에서 읽어온 경기장 이름 사용
                insertStmtKblCd.setString(4, homeTeam);
                insertStmtKblCd.setBigDecimal(5, new BigDecimal(cleanedCrowd + ".00000"));

                int inserted = insertStmtKblCd.executeUpdate();
                if (inserted > 0) {
                    System.out.println("✅ KBL_CD 테이블에 삽입됨: " + matchDe + " / " + homeTeam + " / " + cleanedCrowd);
                }

                // colct_sports_match_info 테이블에 데이터 업데이트
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
                    notUpdatedCount++;
                    System.out.println("❌ 업데이트 실패: " + matchDe + " / " + homeTeam);
                }
            }

            System.out.println("🎉 관중수 업데이트 완료");
            System.out.println("🎯 DB 내 마지막 경기일: " + latestMatchDate + " / 실제 업데이트된 경기 수: " + updatedCount + "건");
            System.out.println("🎯 업데이트되지 않은 경기 수: " + notUpdatedCount + "건");

            // DB에서 마지막 경기일 이후로 추가된 경기 수 출력
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
}
