package com.culture.crawler.Update.old;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.*;

public class KLeagueAttendanceCrawlerUpdate1 {
    public static void main(String[] args) {
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver\\chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        try {
            int totalInsertCount = 0;
            String minInsertedDate = null;
            String maxInsertedDate = null;
            String latestMatchDate = "20230101";  // 기본값: 날짜 없음

            // DB에서 마지막 K리그 경기일 가져오기
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul",
                    "root", "1234"
            )) {
                java.sql.Statement stmt = conn.createStatement();
                java.sql.ResultSet rs = stmt.executeQuery(
                    "SELECT MAX(MATCH_DE) FROM colct_sports_match_info WHERE GRP_NM = 'KLEAGUE'"
                );
                if (rs.next() && rs.getString(1) != null) {
                    latestMatchDate = rs.getString(1);
                }
                rs.close();
                stmt.close();
                System.out.println("📌 마지막 저장된 K리그 경기일: " + latestMatchDate);
            } catch (Exception e) {
                System.err.println("❌ K리그 마지막 날짜 조회 실패: " + e.getMessage());
            }

            driver.get("https://data.kleague.com/");
            driver.manage().window().maximize();

            driver.switchTo().frame(1);
            wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("li:nth-child(1) > a"))).click();

            driver.switchTo().defaultContent(); driver.switchTo().frame(1);
            wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("li:nth-child(5) > a"))).click();

            driver.switchTo().defaultContent(); driver.switchTo().frame(1);
            wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("#subMenuLayer_0016 > li > a:nth-child(6)"))).click();

            driver.switchTo().defaultContent(); driver.switchTo().frame(1);
            wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@id='subMenuLayer_0029']/li/a[7]"))).click();

            driver.switchTo().defaultContent(); driver.switchTo().frame(1);
            wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@id='subMenuLayer_0492']/li/a[1]"))).click();

            driver.switchTo().defaultContent(); driver.switchTo().frame(1);
            By tableBy = By.cssSelector("#commonview > div.searchDataset.sub-team-table table#tableCrowd01");
            wait.until(ExpectedConditions.visibilityOfElementLocated(tableBy));

            new Select(wait.until(ExpectedConditions.elementToBeClickable(By.id("pageViewCount")))).selectByValue("500");
            js.executeScript("fnChange();");
            Thread.sleep(2000);
            System.out.println("✅ 500개 보기 선택 완료");

            Select leagueSelect = new Select(wait.until(ExpectedConditions.elementToBeClickable(By.id("leagueId"))));
            leagueSelect.selectByValue("5");  // 5 = 통산
            js.executeScript("fnChange();");
            Thread.sleep(2000);
            System.out.println("✅ 리그 선택 완료: 통산");

            // `latestMatchDate`에 기반하여 크롤링 시작 연도 계산
            int startYear = Integer.parseInt(latestMatchDate.substring(0, 4));

            // 종료 연도는 현재 연도로 설정
            int endYear = LocalDate.now().getYear();

            for (int year = startYear; year <= endYear; year++) {
                driver.switchTo().defaultContent(); driver.switchTo().frame(1);
                new Select(wait.until(ExpectedConditions.elementToBeClickable(By.id("meetYear")))).selectByValue(String.valueOf(year));
                js.executeScript("fnChange();");
                Thread.sleep(2000);

                By paginationUlBy = By.cssSelector("#commonview > div.pagination > ul");
                int currentPageIndex = 0;
                boolean hasNextPage = true;

                while (hasNextPage) {
                    driver.switchTo().defaultContent(); driver.switchTo().frame(1);
                    WebElement table = wait.until(ExpectedConditions.visibilityOfElementLocated(tableBy));
                    List<WebElement> rows = table.findElements(By.cssSelector("tbody > tr"));

                    for (WebElement row : rows) {
                        List<WebElement> cells = row.findElements(By.tagName("td"));
                        if (cells.size() < 13) continue;

                        String rawDate = cells.get(5).getText(); // "2025/05/03"
                        String fullDate = rawDate.replaceAll("[^0-9]", ""); // "20250503"

                        // DB에서 마지막 날짜 이후로만 크롤링하도록 필터링 (latestMatchDate 이후부터)
                        if (fullDate.compareTo(latestMatchDate) <= 0) {
                            System.out.println("⏩ 생략: " + fullDate + " 경기");
                            continue;
                        }

                        // 신규 데이터 추가 카운팅
                        totalInsertCount++;
                        if (minInsertedDate == null || fullDate.compareTo(minInsertedDate) < 0) minInsertedDate = fullDate;
                        if (maxInsertedDate == null || fullDate.compareTo(maxInsertedDate) > 0) maxInsertedDate = fullDate;

                        String baseYear = fullDate.substring(0, 4);
                        String baseMt = fullDate.substring(4, 6);
                        String baseDay = fullDate.substring(6, 8);
                        String grpNm = "KLEAGUE";
                        String leaNm = cells.get(2).getText();
                        String homeTeam = cells.get(6).getText();
                        String awayTeam = cells.get(7).getText();
                        String stadium = cells.get(9).getText();
                        String viewCount = cells.get(10).getText().replaceAll("[^0-9]", "") + ".00000";

                        // DB에 데이터 삽입
                        insertKLeagueToDb(
                                fullDate, baseYear, baseMt, baseDay, grpNm, leaNm,
                                homeTeam, awayTeam, stadium, viewCount, today
                        );
                    }

                    try {
                        WebElement paginationUl = wait.until(ExpectedConditions.visibilityOfElementLocated(paginationUlBy));
                        List<WebElement> pageLinks = paginationUl.findElements(By.tagName("li"));

                        if (currentPageIndex >= pageLinks.size() - 1) {
                            hasNextPage = false;
                        } else {
                            WebElement nextPageLi = pageLinks.get(currentPageIndex + 1);
                            js.executeScript("arguments[0].scrollIntoView(true);", nextPageLi);
                            nextPageLi.click();
                            currentPageIndex++;
                            Thread.sleep(3000);
                        }
                    } catch (Exception e) {
                        hasNextPage = false;
                    }
                }

                System.out.println("✅ " + year + "년 경기 크롤링 및 DB 저장 완료 (CSV 저장은 생략됨)");
            }

            // 출력
            System.out.println("🎯 K리그 총 추가 건수: " + totalInsertCount);
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

 // 데이터 삽입 함수
    public static void insertKLeagueToDb(
            String matchDe, String baseYear, String baseMt, String baseDay,
            String grpNm, String leaNm, String homeTeam, String awayTeam,
            String stadium, String crowd, String today
    ) {
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul",
                "root", "1234"
        )) {
            // 1. 먼저 colct_sports_match_info_KLEAGUE 테이블에 삽입
            String insertKleagueSql = "INSERT INTO colct_sports_match_info_KLEAGUE " +
                    "(MATCH_DE, LEA_NM, HOME_TEAM_NM, AWAY_TEAM_NM, STDM_NM, SPORTS_VIEWNG_NMPR_CO) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            
            PreparedStatement pstmtKleague = conn.prepareStatement(insertKleagueSql);
            pstmtKleague.setString(1, matchDe);
            pstmtKleague.setString(2, leaNm);
            pstmtKleague.setString(3, homeTeam);
            pstmtKleague.setString(4, awayTeam);
            pstmtKleague.setString(5, stadium);
            pstmtKleague.setBigDecimal(6, new java.math.BigDecimal(crowd));

            int kLeagueRowsInserted = pstmtKleague.executeUpdate();
            pstmtKleague.close();

            if (kLeagueRowsInserted > 0) {
                System.out.println("✅ KLEAGUE 테이블에 데이터 삽입 완료: " + matchDe + " " + homeTeam + " vs " + awayTeam);
            } else {
                System.out.println("❌ KLEAGUE 테이블 삽입 실패: " + matchDe);
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
