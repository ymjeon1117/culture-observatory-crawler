package com.culture.crawler.spring;

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
import java.util.Optional;
import java.util.stream.Collectors;
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

import com.culture.util.LogUtil;

public class KBLIntegratedCrawlerDirectUpdate {
    public static void main(String[] args) {
        try {
            // 1. 경기일정 크롤링 및 DB 저장 → 마지막 KBL 기준일들 리턴
            Map<String, LocalDate> result = runMatchScheduleCrawlerAndInsertToDB();
            LocalDate matchInfoBaseDate = result.get("latestMatchDateColct");  // 맷치인포 기준일
            LocalDate crowdBaseDate = result.get("latestMatchDateKblCd");      // 관중수 기준일

            // 2. 관중수 엑셀 다운로드
            Path excelFile = runCrowdExcelDownload();

            // 3. 관중수 Excel → DB 삽입 + 통합 테이블 삽입
            if (excelFile != null && result != null) {
                updateCrowdFromExcel(
                    excelFile,
                    crowdBaseDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                );

                insertToMatchInfoTable(matchInfoBaseDate);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

	
	public static Map<String, LocalDate> runMatchScheduleCrawlerAndInsertToDB() throws Exception {
		
	    System.setProperty("webdriver.chrome.driver", "C:\\chromedriver\\chromedriver.exe");

	    ChromeOptions options = new ChromeOptions();
	    options.addArguments("--remote-allow-origins=*");
	    WebDriver driver = new ChromeDriver(options);
	    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

	    String latestMatchDateColct = "20230101";
	    String latestMatchDateKblSc = "20230101";
	    String latestMatchDateKblCd = "20230101";

	    // DB 연결
	    try (Connection conn = DriverManager.getConnection(
	            "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul", "root", "1234")) {
	        conn.setAutoCommit(false);
	        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
	        LogUtil.insertLog("KBL 스포츠 관람", "KBL 스포츠 경기 정보 수집", "colct_sports_match_info_KBL_SC", "STARTED", null, null, null, "", today);

	        // 기준일 조회 - colct_sports_match_info
	        try (PreparedStatement stmt = conn.prepareStatement(
	                "SELECT MAX(MATCH_DE) FROM colct_sports_match_info WHERE GRP_NM = 'KBL'")) {
	            ResultSet rs = stmt.executeQuery();
	            if (rs.next() && rs.getString(1) != null) {
	                latestMatchDateColct = rs.getString(1);
	            }
	            rs.close();
	        }

	        // 기준일 조회 - colct_sports_match_info_KBL_SC
	        try (PreparedStatement stmt = conn.prepareStatement(
	                "SELECT MAX(MATCH_DE) FROM colct_sports_match_info_KBL_SC")) {
	            ResultSet rs = stmt.executeQuery();
	            if (rs.next() && rs.getString(1) != null) {
	                latestMatchDateKblSc = rs.getString(1);
	            }
	            rs.close();
	        }
	        

	     // 기준일 조회 - colct_sports_match_info_KBL_CD
	     try (PreparedStatement stmt = conn.prepareStatement(
	             "SELECT MAX(MATCH_DE) FROM colct_sports_match_info_KBL_CD")) {
	         ResultSet rs = stmt.executeQuery();
	         if (rs.next() && rs.getString(1) != null) {
	             latestMatchDateKblCd = rs.getString(1);
	         }
	         rs.close();
	     }

	     System.out.println("📌 KblCd 테이블 마지막 저장된 KBL 경기일: " + latestMatchDateKblCd);
         System.out.println("📌 KblSc 테이블 마지막 저장된 KBL 경기일: " + latestMatchDateColct);
            System.out.println("📌 매치 테이블 마지막 저장된 KBL 경기일: " + latestMatchDateKblSc);



	        driver.get("https://www.kbl.or.kr/match/schedule?type=SCHEDULE");
	        driver.manage().window().maximize();
	        Thread.sleep(3000);

	        String[] currentMonthParts = driver.findElement(By.cssSelector(
	                "#root > main > div > div.contents > div.filter-wrap > div > ul > li:nth-child(2) > p"))
	                .getText().trim().split("\\.");
	        int currentYear = Integer.parseInt(currentMonthParts[0].trim());
	        int currentMonth = Integer.parseInt(currentMonthParts[1].trim());

	        // 더 과거 기준일로 수집기준 설정
	        String baseDate = (latestMatchDateColct.compareTo(latestMatchDateKblSc) <= 0)
	                          ? latestMatchDateColct : latestMatchDateKblSc;
	        LocalDate latestDate = LocalDate.parse(baseDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

	        System.out.println("📌 마지막 경기일: " + latestDate);
	        
	        // 크롤링 시작일 결정
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
	                if (matchDe.compareTo(baseDate) < 0) continue;

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
	        final String finalColctDate = latestMatchDateColct;
	        final String finalKblScDate = latestMatchDateKblSc;

	        List<Map<String, String>> listForColct = parsedList.stream()
	            .filter(row -> row.get("matchDe").compareTo(finalColctDate) >= 0)
	            .collect(Collectors.toList());

	        List<Map<String, String>> listForKblSc = parsedList.stream()
	            .filter(row -> row.get("matchDe").compareTo(finalKblScDate) >= 0)
	            .collect(Collectors.toList());


	        // ✅ 첫 번째 테이블 처리	        
	        int totalKblSc = listForKblSc.size();
	        int insertedKblSc = 0;
	        // ✅ 삽입 전 삭제 - colct_sports_match_info_KBL_SC
	        try (PreparedStatement deleteStmt = conn.prepareStatement(
	                "DELETE FROM colct_sports_match_info_KBL_SC WHERE MATCH_DE = ?")) {
	            deleteStmt.setString(1, latestMatchDateKblSc);
	            deleteStmt.executeUpdate();
	        }
	        try (
	            PreparedStatement insertStmtKblSc = conn.prepareStatement("""
	                INSERT INTO colct_sports_match_info_KBL_SC (
	                    MATCH_DE, LEA_NM, STDM_NM, HOME_TEAM_NM, AWAY_TEAM_NM
	                ) VALUES (?, ?, ?, ?, ?)
	            """)
	        ) {
	            conn.setAutoCommit(false);
	            for (Map<String, String> row : listForKblSc) {
	                insertStmtKblSc.setString(1, row.get("matchDe"));
	                insertStmtKblSc.setString(2, row.get("gameType"));
	                insertStmtKblSc.setString(3, row.get("stadium"));
	                insertStmtKblSc.setString(4, row.get("homeTeam"));
	                insertStmtKblSc.setString(5, row.get("awayTeam"));
	                insertStmtKblSc.addBatch();
	            }
	            insertedKblSc = insertStmtKblSc.executeBatch().length;
	            conn.commit();
	            LogUtil.insertLog("KBL 스포츠 관람", "KBL 스포츠 경기 정보 수집", "colct_sports_match_info_KBL_SC", "SUCCESS",
	                    totalKblSc, insertedKblSc, 0, "", today);
	            LogUtil.insertFlag(today, "colct_sports_match_info_KBL_SC", true); // ✅ 여기에 추가
	            System.out.println("✅ KBL_SC 테이블 배치 성공");
	        } catch (SQLException e) {
	            conn.rollback();
	            LogUtil.insertLog("KBL 스포츠 관람", "KBL 스포츠 경기 정보 수집", "colct_sports_match_info_KBL_SC", "FAILED",
	                    totalKblSc, 0, totalKblSc, e.getMessage(), today);
	            System.err.println("❌ KBL_SC 배치 실패: " + e.getMessage());
	        }
	        System.out.println("✅ KBL 스케줄(관중정보x) DB 저장 시도 완료");
	        Map<String, LocalDate> result = new HashMap<>();
	        result.put("latestMatchDateColct", LocalDate.parse(latestMatchDateColct, DateTimeFormatter.ofPattern("yyyyMMdd")));
	        result.put("latestMatchDateKblCd", LocalDate.parse(latestMatchDateKblCd, DateTimeFormatter.ofPattern("yyyyMMdd")));
	        return result;

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
    
    public static void updateCrowdFromExcel(Path excelPath, String latestMatchDateCd) throws Exception {
        Workbook workbook = null;
        FileInputStream fis = null;

        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul", "root", "1234")) {

            fis = new FileInputStream(excelPath.toFile());
            workbook = new XSSFWorkbook(fis);

            Sheet sheet = workbook.getSheetAt(0);
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            LogUtil.insertLog("KBL 스포츠 관람", "KBL 스포츠 경기 정보 수집", "colct_sports_match_info_KBL_CD", "STARTED", null, null, null, "", today);

            LocalDate latestDate = LocalDate.parse(latestMatchDateCd, DateTimeFormatter.ofPattern("yyyyMMdd"));

            List<Map<String, String>> dataList = new ArrayList<>();
            List<String> allMatchDeList = new ArrayList<>(); // ✅ 모든 matchDe 수집용

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

                LocalDate matchDate = LocalDate.of(year, mm, dd);
                String matchDe = matchDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

                allMatchDeList.add(matchDe); // ✅ 수집

//                if (!matchDate.isAfter(latestDate)) continue;
                if (matchDate.isBefore(latestDate)) continue;
                
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

            // ✅ 전체 matchDe 목록 출력
            System.out.println("\n📜 엑셀 내 추출된 matchDe 전체 목록:");
            allMatchDeList.stream().sorted().forEach(d -> System.out.println(" - " + d));

            // ✅ 마지막 matchDe 기준 비교 출력
            Optional<String> maxExcelMatchDe = allMatchDeList.stream().max(Comparator.naturalOrder());
            System.out.println("\n📌 엑셀 기준 마지막 matchDe: " + maxExcelMatchDe.orElse("없음"));
            System.out.println("📌 DB 기준 최신 matchDe(latestMatchDateSc): " + latestMatchDateCd);

            if (maxExcelMatchDe.isPresent()) {
                int cmp = maxExcelMatchDe.get().compareTo(latestMatchDateCd);
                if (cmp > 0) {
                    System.out.println("✅ 엑셀의 마지막 날짜가 DB 기준일보다 최신입니다. (삽입 대상 존재)");
                } else if (cmp == 0) {
                    System.out.println("⚠️ 엑셀 마지막 날짜가 DB 기준일과 동일합니다. (삽입 건수 없을 수 있음)");
                } else {
                    System.out.println("❌ 엑셀의 마지막 날짜가 DB 기준일보다 과거입니다. (삽입 대상 없음)");
                }
            }

            System.out.println("\n📋 필터링 후 최종 데이터 수(dataList): " + dataList.size());
            for (Map<String, String> row : dataList) {
                System.out.println("👉 " + row);
            }


            int insertedCount = 0;
            int updatedCount = 0;
            int notUpdatedCount = 0;
            System.out.println("📋 필터링 후 최종 데이터 수: " + dataList.size());
            for (Map<String, String> row : dataList) {
                System.out.println("👉 " + row);
            }
            // ✅ colct_sports_match_info_KBL_CD 테이블: INSERT
            
            try (PreparedStatement deleteStmt = conn.prepareStatement(
                    "DELETE FROM colct_sports_match_info_KBL_CD WHERE MATCH_DE = ?")) {
                deleteStmt.setString(1, latestMatchDateCd);  // SC 테이블 기준일
                deleteStmt.executeUpdate();
            }

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

                LogUtil.insertLog("KBL 스포츠 관람", "KBL 스포츠 경기 정보 수집", "colct_sports_match_info_KBL_CD", "SUCCESS",
                        dataList.size(), insertedCount, dataList.size() - insertedCount, "", today);
                LogUtil.insertFlag(today, "colct_sports_match_info_KBL_CD", true); // ✅ 여기에 추가
                System.out.println("✅ KBL_CD 테이블 배치 완료: " + insertedCount + "건");

            } catch (SQLException e) {
                conn.rollback();
                LogUtil.insertLog("KBL 스포츠 관람", "KBL 스포츠 경기 정보 수집", "colct_sports_match_info_KBL_CD", "FAILED",
                        dataList.size(), 0, dataList.size(), e.getMessage(), today);
                System.err.println("❌ KBL_CD 배치 실패: " + e.getMessage());
            }

            // ✅ 추가된 경기 수 확인
            int addedGamesCount = 0;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM colct_sports_match_info WHERE MATCH_DE > ? AND GRP_NM = 'KBL'")) {
                stmt.setString(1, latestMatchDateCd);
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
    
    public static void insertToMatchInfoTable(LocalDate baseDate) throws Exception {
        String baseDateStr = baseDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        LogUtil.insertLog("KBL 스포츠 관람", "KBL 스포츠 경기 정보 수집", "colct_sports_match_info", "STARTED", null, null, null, "", today);

        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul", "root", "1234")) {

            // 1. baseDate 이후 두 테이블 JOIN
        	String sql = """
        		    SELECT 
        		        sc.MATCH_DE,
        		        sc.LEA_NM,
        		        sc.STDM_NM,
        		        sc.HOME_TEAM_NM,
        		        sc.AWAY_TEAM_NM,
        		        cd.SPORTS_VIEWNG_NMPR_CO
        		    FROM colct_sports_match_info_KBL_SC sc
        		    INNER JOIN colct_sports_match_info_KBL_CD cd
        		        ON sc.MATCH_DE = cd.MATCH_DE AND sc.HOME_TEAM_NM = cd.HOME_TEAM_NM
        		    WHERE sc.MATCH_DE >= ?
        		    ORDER BY sc.MATCH_DE
        		""";


            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, baseDateStr);
            ResultSet rs = stmt.executeQuery();

            List<Map<String, String>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, String> row = new HashMap<>();
                String matchDe = rs.getString("MATCH_DE");
                row.put("matchDe", matchDe);
                row.put("baseYear", matchDe.substring(0, 4));
                row.put("baseMonth", matchDe.substring(4, 6));
                row.put("baseDay", matchDe.substring(6, 8));
                row.put("gameType", rs.getString("LEA_NM"));
                row.put("stadium", rs.getString("STDM_NM"));
                row.put("homeTeam", rs.getString("HOME_TEAM_NM"));
                row.put("awayTeam", rs.getString("AWAY_TEAM_NM"));
                row.put("crowd", rs.getString("SPORTS_VIEWNG_NMPR_CO"));
                rows.add(row);
            }

            rs.close();
            stmt.close();

            System.out.println("📋 최종 삽입할 건수: " + rows.size());

            // 2. 기존 데이터 삭제
            try (PreparedStatement deleteStmt = conn.prepareStatement(
                    "DELETE FROM colct_sports_match_info WHERE GRP_NM = 'KBL' AND MATCH_DE = ?")) {
                deleteStmt.setString(1, baseDateStr);
                deleteStmt.executeUpdate();
            }

            // 3. INSERT 실행
            try (PreparedStatement insertStmt = conn.prepareStatement("""
                INSERT INTO colct_sports_match_info (
                    MATCH_DE, BASE_YEAR, BASE_MT, BASE_DAY, GRP_NM, LEA_NM, HOME_TEAM_NM, AWAY_TEAM_NM,
                    STDM_NM, SPORTS_VIEWNG_NMPR_CO, COLCT_DE, UPDT_DE
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
                conn.setAutoCommit(false);
                for (Map<String, String> row : rows) {
                    insertStmt.setString(1, row.get("matchDe"));
                    insertStmt.setString(2, row.get("baseYear"));
                    insertStmt.setString(3, row.get("baseMonth"));
                    insertStmt.setString(4, row.get("baseDay"));
                    insertStmt.setString(5, "KBL");
                    insertStmt.setString(6, row.get("gameType"));
                    insertStmt.setString(7, row.get("homeTeam"));
                    insertStmt.setString(8, row.get("awayTeam"));
                    insertStmt.setString(9, row.get("stadium"));

                    if (row.get("crowd") != null) {
                        insertStmt.setBigDecimal(10, new BigDecimal(row.get("crowd")));
                    } else {
                        insertStmt.setNull(10, Types.DECIMAL);
                    }

                    insertStmt.setString(11, today);
                    insertStmt.setString(12, today);
                    insertStmt.addBatch();
                }
                int inserted = insertStmt.executeBatch().length;
                conn.commit();
                LogUtil.insertLog("KBL 스포츠 관람", "KBL 스포츠 경기 정보 수집", "colct_sports_match_info", "SUCCESS",
                        rows.size(), inserted, 0, "", today);
                LogUtil.insertFlag(today, "colct_sports_match_info@KBL", true); // ✅ 여기에 추가
                System.out.println("✅ colct_sports_match_info 삽입 완료: " + inserted + "건");

            } catch (SQLException e) {
                conn.rollback();
                LogUtil.insertLog("KBL 스포츠 관람", "KBL 스포츠 경기 정보 수집", "colct_sports_match_info", "FAILED",
                        rows.size(), 0, 0, e.getMessage(), today);
                System.err.println("❌ 삽입 실패: " + e.getMessage());
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
