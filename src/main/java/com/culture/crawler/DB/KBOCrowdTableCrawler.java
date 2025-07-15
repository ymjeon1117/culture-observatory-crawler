package com.culture.crawler.DB;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.*;

public class KBOCrowdTableCrawler {
    public static void main(String[] args) throws Exception {
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver\\chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        int startYear = 2023;
        int endYear = 2025;
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        try {
            driver.get("https://www.koreabaseball.com/Record/Crowd/GraphDaily.aspx");
            driver.manage().window().maximize();

            for (int year = startYear; year <= endYear; year++) {
            	Select seasonSelect = new Select(wait.until(ExpectedConditions.elementToBeClickable(
            		    By.id("cphContents_cphContents_cphContents_ddlSeason"))));
            		seasonSelect.selectByValue(String.valueOf(year));

            		// __doPostBack 불필요! 페이지 자동 reload 됨
            		Thread.sleep(2000);


                WebElement table = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("#cphContents_cphContents_cphContents_udpRecord > table")));
                List<WebElement> rows = table.findElements(By.tagName("tr"));

                String csvFile = "kbo_matchinfo_" + year + ".csv";
//                PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(csvFile, false)));
//
//                // ✅ DB용 헤더 (MATCH_SEQ_NO 없음)
//                writer.println("MATCH_DE,BASE_YEAR,BASE_MT,BASE_DAY,GRP_NM,LEA_NM,HOME_TEAM_NM,AWAY_TEAM_NM,STDM_NM,SPORTS_VIEWNG_NMPR_CO,COLCT_DE,UPDT_DE");

                for (int i = 1; i < rows.size(); i++) {
                    List<WebElement> cols = rows.get(i).findElements(By.tagName("td"));
                    if (cols.size() < 6) continue;

                    String rawDate = cols.get(0).getText().trim(); // 실제 값: 2025/03/22
                    LocalDate matchDate = LocalDate.parse(rawDate, DateTimeFormatter.ofPattern("yyyy/MM/dd"));

                    String matchDe = matchDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                    String baseYear = matchDate.format(DateTimeFormatter.ofPattern("yyyy"));
                    String baseMt = matchDate.format(DateTimeFormatter.ofPattern("MM"));
                    String baseDay = matchDate.format(DateTimeFormatter.ofPattern("dd"));

                    String home = cols.get(2).getText().trim();
                    String away = cols.get(3).getText().trim();
                    String stadium = cols.get(4).getText().trim();
                    String crowdRaw = cols.get(5).getText().trim().replace(",", "").replace("명", "");
                    if (crowdRaw.isEmpty()) crowdRaw = "0";
                    String crowd = crowdRaw + ".00000";

//                    String line = String.join(",",
//                        matchDe, baseYear, baseMt, baseDay, "KBO", "정규리그",
//                        home, away, stadium, crowd, today, today
//                    );
//                    writer.println(line);
                    insertKBOCrowdToDb(
                            matchDe, baseYear, baseMt, baseDay,
                            "KBO", "정규리그",
                            home, away, stadium, crowd, today
                        );
                }

//                writer.close();
                System.out.println("✅ " + year + "년 경기 CSV 저장 완료: " + csvFile);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
    public static void insertKBOCrowdToDb(
            String matchDe, String baseYear, String baseMt, String baseDay,
            String grpNm, String leaNm, String homeTeam, String awayTeam,
            String stadium, String crowd, String today
    ) {
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul",
                "root", // ✅ 사용자에 맞게 수정
                "1234" // ✅ 실제 비밀번호로 수정
        )) {
            String sql = "INSERT INTO colct_sports_match_info " +
                    "(MATCH_DE, BASE_YEAR, BASE_MT, BASE_DAY, GRP_NM, LEA_NM, HOME_TEAM_NM, AWAY_TEAM_NM, " +
                    "STDM_NM, SPORTS_VIEWNG_NMPR_CO, COLCT_DE, UPDT_DE) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            java.sql.PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, matchDe);
            pstmt.setString(2, baseYear);
            pstmt.setString(3, baseMt);
            pstmt.setString(4, baseDay);
            pstmt.setString(5, grpNm);
            pstmt.setString(6, leaNm);
            pstmt.setString(7, homeTeam);
            pstmt.setString(8, awayTeam);
            pstmt.setString(9, stadium);
            pstmt.setBigDecimal(10, new java.math.BigDecimal(crowd));
            pstmt.setString(11, today);
            pstmt.setString(12, today);

            pstmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("❌ DB insert 실패: " + e.getMessage());
        }
    }

}
