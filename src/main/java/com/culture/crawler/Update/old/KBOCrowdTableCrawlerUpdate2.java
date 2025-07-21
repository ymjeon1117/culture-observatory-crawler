package com.culture.crawler.Update.old;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

public class KBOCrowdTableCrawlerUpdate2 {
    public static void main(String[] args) throws Exception {
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver\\chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // DB에서 최신 경기를 기준으로 크롤링
        String latestMatchDate = getLatestMatchDateFromDB();
        System.out.println("📌 마지막 저장된 KBO 경기일: " + latestMatchDate);

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        try {
            int totalInsertCount = 0;
            String minInsertedDate = null;
            String maxInsertedDate = null;

            driver.get("https://www.koreabaseball.com/Record/Crowd/GraphDaily.aspx");
            driver.manage().window().maximize();

            // DB에서 마지막 경기일을 기준으로 크롤링 시작 연도 계산
            int latestYear = Integer.parseInt(latestMatchDate.substring(0, 4));
            System.out.println("[INFO] 마지막 경기일 기준 연도: " + latestYear);

            // 크롤링할 연도 범위 설정 (최신 경기일 이후 연도부터 시작)
            for (int year = latestYear; year <= LocalDate.now().getYear(); year++) {
                // 연도 선택
                Select seasonSelect = new Select(wait.until(ExpectedConditions.elementToBeClickable(
                    By.id("cphContents_cphContents_cphContents_ddlSeason"))));
                seasonSelect.selectByValue(String.valueOf(year));

                // 검색 버튼 클릭
                WebElement searchBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.id("cphContents_cphContents_cphContents_btnSearch")));
                searchBtn.click();

                // 테이블 로딩 대기 (AJAX 처리 시간 필요)
                Thread.sleep(1500);  // 또는 명확한 로딩 wait이 가능하면 교체

                // 테이블 다시 조회
                WebElement table = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("#cphContents_cphContents_cphContents_udpRecord > table")));
                List<WebElement> rows = table.findElements(By.tagName("tr"));

                int yearInsertCount = 0; // 년도별 추가 건수 카운트

                for (int i = 1; i < rows.size(); i++) {
                    try {
                        List<WebElement> cols = rows.get(i).findElements(By.tagName("td"));
                        if (cols.size() < 6) continue;

                        String rawDate = cols.get(0).getText().trim();
                        LocalDate matchDate = LocalDate.parse(rawDate, DateTimeFormatter.ofPattern("yyyy/MM/dd"));

                        String matchDe = matchDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

                        // DB에서 최신 날짜 이후로만 크롤링하도록 필터링
                        if (matchDe.compareTo(latestMatchDate) <= 0) {
                            System.out.println("⏩ 생략: " + matchDe + " 경기");
                            continue;
                        }

                        String baseYear = matchDate.format(DateTimeFormatter.ofPattern("yyyy"));
                        String baseMt = matchDate.format(DateTimeFormatter.ofPattern("MM"));
                        String baseDay = matchDate.format(DateTimeFormatter.ofPattern("dd"));

                        String home = cols.get(2).getText().trim();
                        String away = cols.get(3).getText().trim();
                        String stadium = cols.get(4).getText().trim();
                        String crowdRaw = cols.get(5).getText().trim().replace(",", "").replace("명", "");
                        if (crowdRaw.isEmpty()) crowdRaw = "0";
                        String crowd = crowdRaw + ".00000";

                        // 데이터 삽입
                        totalInsertCount++;
                        yearInsertCount++;  // 해당 년도에 추가된 경기 수 증가

                        if (minInsertedDate == null || matchDe.compareTo(minInsertedDate) < 0) minInsertedDate = matchDe;
                        if (maxInsertedDate == null || matchDe.compareTo(maxInsertedDate) > 0) maxInsertedDate = matchDe;

                        insertKBOCrowdToDb(
                            matchDe, baseYear, baseMt, baseDay,
                            "KBO", "정규리그",
                            home, away, stadium, crowd, today
                        );
                    } catch (StaleElementReferenceException se) {
                        System.err.println("⚠️ StaleElement: " + i + "번째 행 건너뜀");
                        continue;
                    } catch (Exception ex) {
                        System.err.println("❌ 데이터 처리 중 오류: " + ex.getMessage());
                        continue;
                    }
                }

                System.out.println("✅ " + year + "년 경기 크롤링 및 DB 저장 완료 (CSV 저장은 생략됨)");
                System.out.println("📅 " + year + "년 추가된 경기 수: " + yearInsertCount);
            }

            System.out.println("🎯 KBO 총 추가 건수: " + totalInsertCount);
            if (totalInsertCount > 0) {
                System.out.println("🗓️ 추가된 경기 날짜 범위: " + minInsertedDate + " ~ " + maxInsertedDate);
            } else {
                System.out.println("📭 추가된 경기 없음 (이미 모두 반영됨)");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    // DB에서 마지막 경기일을 가져오는 함수
    private static String getLatestMatchDateFromDB() {
        String latestMatchDate = "20230101";  // 기본값

        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul", "root", "1234")) {

            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT MAX(MATCH_DE) FROM colct_sports_match_info WHERE GRP_NM = 'KBO'");
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getString(1) != null) {
                latestMatchDate = rs.getString(1);
            }
            rs.close();
            stmt.close();
        } catch (Exception e) {
            System.err.println("❌ KBO 마지막 날짜 조회 실패: " + e.getMessage());
        }

        return latestMatchDate;
    }
 // 데이터 삽입 함수
    public static void insertKBOCrowdToDb(
            String matchDe, String baseYear, String baseMt, String baseDay,
            String grpNm, String leaNm, String homeTeam, String awayTeam,
            String stadium, String crowd, String today
    ) {
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul",
                "root", "1234"
        )) {
            // 1. 먼저 colct_sports_match_info_KBO 테이블에 삽입
            String insertKboSql = "INSERT INTO colct_sports_match_info_KBO " +
                    "(MATCH_DE, HOME_TEAM_NM, AWAY_TEAM_NM, STDM_NM, SPORTS_VIEWNG_NMPR_CO) " +
                    "VALUES (?, ?, ?, ?, ?)";
            PreparedStatement pstmtKbo = conn.prepareStatement(insertKboSql);
            pstmtKbo.setString(1, matchDe);
            pstmtKbo.setString(2, homeTeam);
            pstmtKbo.setString(3, awayTeam);
            pstmtKbo.setString(4, stadium);
            pstmtKbo.setBigDecimal(5, new java.math.BigDecimal(crowd)); // 관중 수

            int kboRowsInserted = pstmtKbo.executeUpdate();
            pstmtKbo.close();

            if (kboRowsInserted > 0) {
                System.out.println("✅ KBO 테이블에 데이터 삽입 완료: " + matchDe + " " + homeTeam + " vs " + awayTeam);
            } else {
                System.out.println("❌ KBO 테이블 삽입 실패: " + matchDe);
                return;  // 삽입 실패 시 함수 종료
            }

            // 2. 그 다음 colct_sports_match_info 테이블에 삽입
            String insertMatchInfoSql = "INSERT INTO colct_sports_match_info " +
                    "(MATCH_DE, BASE_YEAR, BASE_MT, BASE_DAY, GRP_NM, LEA_NM, HOME_TEAM_NM, AWAY_TEAM_NM, " +
                    "STDM_NM, SPORTS_VIEWNG_NMPR_CO, COLCT_DE, UPDT_DE) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            PreparedStatement pstmtMatchInfo = conn.prepareStatement(insertMatchInfoSql);
            pstmtMatchInfo.setString(1, matchDe);
            pstmtMatchInfo.setString(2, baseYear);
            pstmtMatchInfo.setString(3, baseMt);
            pstmtMatchInfo.setString(4, baseDay);
            pstmtMatchInfo.setString(5, grpNm);
            pstmtMatchInfo.setString(6, leaNm);
            pstmtMatchInfo.setString(7, homeTeam);
            pstmtMatchInfo.setString(8, awayTeam);
            pstmtMatchInfo.setString(9, stadium);
            pstmtMatchInfo.setBigDecimal(10, new java.math.BigDecimal(crowd));
            pstmtMatchInfo.setString(11, today);
            pstmtMatchInfo.setString(12, today);

            pstmtMatchInfo.executeUpdate();
            pstmtMatchInfo.close();

            System.out.println("✅ MATCH_INFO 테이블에 데이터 삽입 완료: " + matchDe + " " + homeTeam + " vs " + awayTeam);

        } catch (Exception e) {
            System.err.println("❌ DB insert 실패: " + e.getMessage());
        }
    }

}
