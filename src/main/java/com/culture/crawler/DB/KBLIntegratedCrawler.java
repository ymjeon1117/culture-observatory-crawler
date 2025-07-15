package com.culture.crawler.DB;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
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

public class KBLIntegratedCrawler {
    public static void main(String[] args) {
        try {
            runCrowdCrawler();         // ✅ 관중 수 엑셀 다운로드
            runMatchScheduleCrawler(); // ✅ 경기 일정 크롤링

            // ✅ CSV 병합
            String baseDir = System.getProperty("user.dir");
            String scheduleCsv = baseDir + File.separator + "kbl_matchinfo_2024_from_schedule.csv";
            String crowdCsv = baseDir + File.separator + "kbl_matchinfo_2024.csv";
            String outputCsv = baseDir + File.separator + "kbl_matchinfo_merged.csv";

//            mergeCrowdAndScheduleCsv(scheduleCsv, crowdCsv, outputCsv);
            insertCrowdAndScheduleToDb(scheduleCsv, crowdCsv);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // ==================== [1] 관중수 크롤링 ====================
    public static void runCrowdCrawler() throws Exception {
        // ✅ [1] 임시 다운로드 폴더 생성 (user.dir 하위로 변경)
        String baseDir = System.getProperty("user.dir"); // 프로젝트 루트
        String timeTag = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        String tempFolderPath = baseDir + File.separator + "kbl_temp_" + timeTag;
        File tempDir = new File(tempFolderPath);
        if (!tempDir.exists()) tempDir.mkdirs();

        // ✅ [2] 다운로드 경로 설정 (보안 문제 없는 위치)
        HashMap<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", tempFolderPath);
        prefs.put("download.prompt_for_download", false);
        prefs.put("profile.default_content_setting_values.automatic_downloads", 1);
        prefs.put("safebrowsing.enabled", true);

        // ✅ [3] 크롬 옵션 설정
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("prefs", prefs);
        options.addArguments("--remote-allow-origins=*");

        // ✅ [4] CSV 저장 위치
        String csvFile = baseDir + File.separator + "kbl_matchinfo_2024.csv";
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // ✅ [5] 크롬 실행
        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(15));

        // ✅ [6] 생성된 폴더 자동 열기
        try {
            System.out.println("📁 생성된 임시폴더: " + tempFolderPath);
            new ProcessBuilder("cmd", "/c", "start", "", "\"" + tempFolderPath + "\"").start();
        } catch (Exception e) {
            System.out.println("❌ 폴더 자동 열기 실패: " + e.getMessage());
        }


        try {
            // ✅ [4] 사이트 진입 및 메뉴 선택
            driver.get("https://kbl.or.kr/record/crowd");
            driver.manage().window().maximize();

            new Select(wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("#s2iZone > div > ul > li > select")))).selectByValue("team");
            Thread.sleep(1000);
            new Select(wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("#s2iZone > div > ul > li:nth-child(2) > select")))).selectByValue("00");
            Thread.sleep(1500);

            WebElement excelButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("#s2iZone > div > div:nth-child(5) > div.mid-title > div > button")));
            excelButton.click();
            System.out.println("📥 엑셀 다운로드 클릭 완료");

         // ✅ [5] 엑셀 다운로드 대기 및 탐색
            Path latestXlsx = null;
            for (int i = 0; i < 20; i++) {
                latestXlsx = getLatestXlsxFile(tempFolderPath);
                if (latestXlsx != null) break;
                Thread.sleep(500);
            }

            if (latestXlsx == null) {
                System.err.println("❌ 엑셀 다운로드 실패: 파일 없음");
                return;
            }
            if (!waitForDownloadComplete(latestXlsx, 10)) {
                System.err.println("❌ 파일 크기 변화 없음, 다운로드 실패로 간주");
                return;
            }
            System.out.println("✅ 엑셀 다운로드 완료: " + latestXlsx);

            // ✅ [6] 엑셀 → CSV 변환
            try (Workbook workbook = new XSSFWorkbook(new FileInputStream(latestXlsx.toFile()));
                 PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(csvFile, false)))) {

                Sheet sheet = workbook.getSheet("Sheet1");
                if (sheet == null) {
                    System.out.println("⚠️ Sheet1 없음, 첫 번째 시트로 대체");
                    sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
                }
                if (sheet == null) {
                    System.err.println("❌ 엑셀 시트를 찾을 수 없음");
                    return;
                }

                writer.println("MATCH_DE,BASE_YEAR,BASE_MT,BASE_DAY,GRP_NM,LEA_NM,HOME_TEAM_NM,AWAY_TEAM_NM,STDM_NM,SPORTS_VIEWNG_NMPR_CO,COLCT_DE,UPDT_DE");

                int convertedCount = 0;
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) {
                        System.out.println("⚠️ row[" + i + "] null → skip");
                        continue;
                    }

                    try {
                        // [1] 시즌 문자열 추출
                        String season = getCellString(row, 0); // 예: "2023-2024"
                        String[] seasonYears = season.split("-");
                        if (seasonYears.length != 2) {
                            System.out.println("⚠️ 시즌 형식 오류, skip: " + season);
                            continue;
                        }
                        int startYear = Integer.parseInt(seasonYears[0].trim());
                        int endYear = Integer.parseInt(seasonYears[1].trim());

                        // [2] 날짜 셀 추출
                        Cell dateCell = row.getCell(6);
                        String rawDate = "";
                        if (dateCell != null) {
                            switch (dateCell.getCellType()) {
                                case STRING -> rawDate = dateCell.getStringCellValue().trim();
                                case NUMERIC -> rawDate = String.format("%.2f", dateCell.getNumericCellValue()); // ex: 10.19
                            }
                        }

                        String homeTeam = getCellString(row, 1);
                        String stadium = getCellString(row, 5);
                        String rawCrowd = getCellString(row, 7);

                        System.out.printf("🔍 row[%d] → 시즌: %s | 날짜: %s | 팀: %s | 경기장: %s | 관중수: %s%n",
                            i, season, rawDate, homeTeam, stadium, rawCrowd);

                        if (!rawDate.matches("\\d{1,2}\\.\\d{1,2}")) {
                            System.out.println("⚠️ 날짜 형식 아님, skip");
                            continue;
                        }

                        String[] parts = rawDate.split("\\.");
                        int mm = Integer.parseInt(parts[0]);
                        int dd = Integer.parseInt(parts[1]);

                        // [3] 상·하반기 기준 연도 판단
                        int baseYear = (mm <= 6) ? endYear : startYear;

                        LocalDate matchDate = LocalDate.of(baseYear, mm, dd);

                        String matchDe = matchDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                        String baseYearStr = matchDate.format(DateTimeFormatter.ofPattern("yyyy"));
                        String baseMonth = matchDate.format(DateTimeFormatter.ofPattern("MM"));
                        String baseDay = matchDate.format(DateTimeFormatter.ofPattern("dd"));

                        if (rawCrowd.isEmpty()) rawCrowd = "0";
                        String crowd = rawCrowd.replace(",", "").replace("명", "").trim() + ".00000";

                        String today1 = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

                        String line = String.join(",", matchDe, baseYearStr, baseMonth, baseDay,
                                "프로스포츠", "KBL", homeTeam, "", stadium, crowd, today1, today1);
                        writer.println(line);
                        convertedCount++;

                    } catch (Exception e) {
                        System.out.println("⚠️ 변환 실패 (row " + i + "): " + e.getMessage());
                    }

                }

                System.out.println("\n📄 총 " + convertedCount + "건 CSV 변환 완료: " + csvFile);
            }

            // ✅ [7] 임시 폴더 삭제
            Files.deleteIfExists(latestXlsx);
            Files.walk(Paths.get(tempFolderPath))
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException ignored) {}
                });
            System.out.println("🧹 임시 폴더 삭제 완료: " + tempFolderPath);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    // ==================== [2] 경기일정 크롤링 ====================
    public static void runMatchScheduleCrawler() throws Exception {
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver\\chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
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

            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            PrintWriter writer = new PrintWriter(new BufferedWriter(
                    new FileWriter("kbl_matchinfo_2024_from_schedule.csv", false)));
            writer.println("MATCH_DE,BASE_YEAR,BASE_MT,BASE_DAY,GRP_NM,LEA_NM,HOME_TEAM_NM,AWAY_TEAM_NM,STDM_NM,SPORTS_VIEWNG_NMPR_CO,COLCT_DE,UPDT_DE");

            for (String targetMonth : targetMonths) {
                while (true) {
                    WebElement dateElem = driver.findElement(By.cssSelector(".filter-wrap ul.date li:nth-child(2) p"));
                    String currentMonth = dateElem.getText().trim();
                    if (currentMonth.equals(targetMonth)) break;

                    WebElement prevBtn = driver.findElement(By.cssSelector(".filter-wrap ul.date li:nth-child(1) button"));
                    prevBtn.click();
                    Thread.sleep(2000);
                }

                System.out.println("[INFO] 수집 중: " + targetMonth);
                extractAndAppendToCsv(driver, writer, today);
            }

            writer.close();
            System.out.println("📄 CSV 저장 완료: kbl_matchinfo_2024_from_schedule.csv");

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

    // 셀 안전 추출
    private static String getCellString(Row row, int colIndex) {
        if (row == null) return "";
        Cell cell = row.getCell(colIndex);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                } else {
                    yield String.valueOf((long) cell.getNumericCellValue());
                }
            }
            default -> "";
        };
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

    private static void extractAndAppendToCsv(WebDriver driver, PrintWriter writer, String today) {
        List<WebElement> gameLists = driver.findElements(By.cssSelector(".cont-box .game-schedule-list"));

        for (WebElement gameList : gameLists) {
            try {
                // ✅ 날짜 추출: id="game-20250401" → 20250401
                String matchDe = gameList.getAttribute("id").replace("game-", "").trim();
                String baseYear = matchDe.substring(0, 4);
                String baseMonth = matchDe.substring(4, 6);
                String baseDay = matchDe.substring(6, 8);

                List<WebElement> items = gameList.findElements(By.cssSelector("ul > li"));
                for (WebElement item : items) {
                    if (item.findElements(By.cssSelector("div.sub")).isEmpty()) continue;

                    try {
                        WebElement desc = item.findElement(By.cssSelector("div.sub > div.desc"));
                        String gameType = desc.findElement(By.cssSelector("span.label")).getText().trim();
                        String location = desc.findElements(By.cssSelector("ul > li")).get(1).getText().trim();

                        List<WebElement> teams = item.findElements(By.cssSelector("div.info ul.versus > li"));
                        if (teams.size() != 2) continue;

                        String homeTeam = teams.get(0).findElements(By.tagName("p")).get(0).getText().trim();
                        String awayTeam = teams.get(1).findElements(By.tagName("p")).get(0).getText().trim();

                        writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                                matchDe, baseYear, baseMonth, baseDay,
                                "KBL", gameType,
                                homeTeam, awayTeam, location,
                                "0.00000", today, today);

                        System.out.println("✅ " + matchDe + " [" + gameType + "] " + homeTeam + " vs " + awayTeam);
                    } catch (Exception e) {
                        System.out.println("[WARN] 경기 단위 파싱 실패: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                System.out.println("[WARN] 날짜 추출 실패: " + e.getMessage());
            }
        }
    }
    public static void mergeCrowdAndScheduleCsv(String scheduleCsvPath, String crowdCsvPath, String outputCsvPath) throws IOException {
        List<String[]> scheduleRows = new ArrayList<>();
        List<String[]> crowdRows = new ArrayList<>();

        try (BufferedReader scheduleReader = new BufferedReader(new FileReader(scheduleCsvPath));
             BufferedReader crowdReader = new BufferedReader(new FileReader(crowdCsvPath))) {

            scheduleReader.readLine(); // skip header
            crowdReader.readLine();

            String line;
            while ((line = scheduleReader.readLine()) != null) {
                scheduleRows.add(line.split(",", -1));
            }

            while ((line = crowdReader.readLine()) != null) {
                crowdRows.add(line.split(",", -1));
            }
        }

        List<String> mergedLines = new ArrayList<>();
        mergedLines.add("MATCH_DE,BASE_YEAR,BASE_MT,BASE_DAY,GRP_NM,LEA_NM,HOME_TEAM_NM,AWAY_TEAM_NM,STDM_NM,SPORTS_VIEWNG_NMPR_CO,COLCT_DE,UPDT_DE");

        for (String[] schedule : scheduleRows) {
            String matchDe = schedule[0].trim();
            String scheduleHome = schedule[6].trim(); // 그대로 사용

            String matchedCrowd = null;

            for (String[] crowd : crowdRows) {
                String crowdDe = crowd[0].trim();
                String crowdHome = extractSecondWordOrOriginal(crowd[6].trim()); // 두 번째 단어만 추출

                if (matchDe.equals(crowdDe) && scheduleHome.equals(crowdHome)) {
                    matchedCrowd = crowd[9];
                    break;
                }
            }

            if (matchedCrowd != null && !matchedCrowd.isEmpty()) {
                schedule[9] = matchedCrowd;
            }

            mergedLines.add(String.join(",", schedule));
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputCsvPath))) {
            for (String l : mergedLines) {
                writer.println(l);
            }
        }

        System.out.println("📁 병합 완료 → 저장: " + outputCsvPath);
    }

    // ✅ 관중수 파일용: 두 번째 단어 추출 (없으면 그대로)
    private static String extractSecondWordOrOriginal(String teamName) {
        String[] parts = teamName.trim().split("\\s+");
        return parts.length >= 2 ? parts[1] : parts[0];
    }
    public static void insertCrowdAndScheduleToDb(String scheduleCsvPath, String crowdCsvPath) throws Exception {
        List<String[]> scheduleRows = new ArrayList<>();
        List<String[]> crowdRows = new ArrayList<>();

        try (BufferedReader scheduleReader = new BufferedReader(new FileReader(scheduleCsvPath));
             BufferedReader crowdReader = new BufferedReader(new FileReader(crowdCsvPath))) {

            scheduleReader.readLine(); // skip header
            crowdReader.readLine();

            String line;
            while ((line = scheduleReader.readLine()) != null) {
                scheduleRows.add(line.split(",", -1));
            }

            while ((line = crowdReader.readLine()) != null) {
                crowdRows.add(line.split(",", -1));
            }
        }

        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul",
                "root",            // 👈 사용자 DB 계정에 맞게 수정
                "1234"    // 👈 비밀번호에 맞게 수정
        )) {
            String sql = "INSERT INTO colct_sports_match_info " +
                    "(MATCH_DE, BASE_YEAR, BASE_MT, BASE_DAY, GRP_NM, LEA_NM, HOME_TEAM_NM, AWAY_TEAM_NM, " +
                    "STDM_NM, SPORTS_VIEWNG_NMPR_CO, COLCT_DE, UPDT_DE) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            java.sql.PreparedStatement pstmt = conn.prepareStatement(sql);
            int count = 0;

            for (String[] schedule : scheduleRows) {
                String matchDe = schedule[0].trim();
                String scheduleHome = schedule[6].trim();

                String matchedCrowd = null;
                for (String[] crowd : crowdRows) {
                    String crowdDe = crowd[0].trim();
                    String crowdHome = extractSecondWordOrOriginal(crowd[6].trim());

                    if (matchDe.equals(crowdDe) && scheduleHome.equals(crowdHome)) {
                        matchedCrowd = crowd[9];
                        break;
                    }
                }

                if (matchedCrowd != null && !matchedCrowd.isEmpty()) {
                    schedule[9] = matchedCrowd;
                }

                pstmt.setString(1,  schedule[0]);
                pstmt.setString(2,  schedule[1]);
                pstmt.setString(3,  schedule[2]);
                pstmt.setString(4,  schedule[3]);
                pstmt.setString(5,  schedule[4]);
                pstmt.setString(6,  schedule[5]);
                pstmt.setString(7,  schedule[6]);
                pstmt.setString(8,  schedule[7]);
                pstmt.setString(9,  schedule[8]);
                pstmt.setBigDecimal(10, new java.math.BigDecimal(schedule[9]));
                pstmt.setString(11, schedule[10]);
                pstmt.setString(12, schedule[11]);

                pstmt.executeUpdate();
                count++;
            }

            System.out.println("✅ DB 저장 완료: " + count + "건 insert됨");
        }
    }

    
}
