package com.culture.crawler;

import java.io.*;
import java.sql.*;
import java.time.*;
import java.util.List;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.*;

public class KBOGameScheduleCrawler {
    public static void main(String[] args) {
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver\\chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        int startYear = 2001;
        int startMonth = 1;

        int endYear = LocalDate.now().getYear();
        int endMonth = LocalDate.now().getMonthValue();

        String jdbcUrl = "jdbc:mysql://localhost:3306/culture_crawler_db?allowPublicKeyRetrieval=true&useSSL=false";
        String dbUser = "root";
        String dbPassword = "1234";

        boolean useDuplicateKeyUpdate = true;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
            Statement stmt = conn.createStatement();

            if (startYear == 0 || startMonth == 0) {
                ResultSet rs = stmt.executeQuery("SELECT MAX(game_date) FROM kbo_schedule");
                if (rs.next() && rs.getDate(1) != null) {
                    LocalDate lastDate = rs.getDate(1).toLocalDate();
                    startYear = lastDate.getYear();
                    startMonth = lastDate.getMonthValue();
                    System.out.printf("📌 DB 기준 → %d-%02d부터 시작%n", startYear, startMonth);
                } else {
                    startYear = endYear;
                    startMonth = endMonth;
                    System.out.println("📌 DB에 데이터 없음 → 오늘 달부터 시작");
                }
            }

            driver.get("https://www.koreabaseball.com/Schedule/Schedule.aspx#");
            driver.manage().window().maximize();

            WebElement yearSelectElem = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("ddlYear")));
            WebElement monthSelectElem = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("ddlMonth")));
            Select yearSelect = new Select(yearSelectElem);
            Select monthSelect = new Select(monthSelectElem);

            for (int year = startYear; year <= endYear; year++) {
                int from = (year == startYear) ? startMonth : 1;
                int to = (year == endYear) ? endMonth : 12;

                for (int month = from; month <= to; month++) {
                    boolean isFirstMonth = (year == startYear && month == startMonth);
                    useDuplicateKeyUpdate = isFirstMonth;

                    yearSelect.selectByValue(String.valueOf(year));
                    Thread.sleep(1000);
                    monthSelect.selectByValue(String.format("%02d", month));
                    Thread.sleep(3000);

                    String fileName = String.format("kbo_schedule_%04d%02d.csv", year, month);
                    PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(fileName, false)));
                    writer.println("일자,시간,홈팀,홈점수,원정점수,원정팀,구장,중계,비고");

                    try {
                        WebElement scheduleTable = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("tblScheduleList")));
                        List<WebElement> rows = scheduleTable.findElements(By.cssSelector("tbody > tr"));

                        String currentDate = "";
                        for (WebElement row : rows) {
                            List<WebElement> tds = row.findElements(By.tagName("td"));
                            if (tds.isEmpty()) continue;

                            int tdIndex = 0;
                            if (tds.get(0).getAttribute("class").contains("day")) {
                                currentDate = tds.get(0).getText().trim();
                                tdIndex = 1;
                            }

                            if (tds.size() < tdIndex + 8) continue;

                            String time = tds.get(tdIndex).getText().trim();
                            String homeTeam = "", awayTeam = "", homeScore = "", awayScore = "";
                            WebElement playTd = tds.get(tdIndex + 1);

                            List<WebElement> spans = playTd.findElements(By.tagName("span"));
                            if (spans.size() >= 1) homeTeam = spans.get(0).getText().trim();
                            if (spans.size() >= 3) awayTeam = spans.get(spans.size() - 1).getText().trim();

                            List<WebElement> emList = playTd.findElements(By.tagName("em"));
                            if (!emList.isEmpty()) {
                                List<WebElement> scoreSpans = emList.get(0).findElements(By.tagName("span"));
                                if (scoreSpans.size() >= 3) {
                                    homeScore = scoreSpans.get(0).getText().trim();
                                    awayScore = scoreSpans.get(2).getText().trim();
                                }
                            }

                            String tvText = tds.get(tdIndex + 4).getText().replaceAll("\\r?\\n", "; ").trim();
                            String stadium = tds.get(tdIndex + 6).getText().trim();
                            String etc = tds.get(tdIndex + 7).getText().trim();

                            writer.println(String.join(",", currentDate, time, homeTeam, homeScore, awayScore, awayTeam, stadium, tvText, etc));
                        }
                    } catch (Exception e) {
                        System.out.println("[WARN] 테이블 파싱 실패: " + e.getMessage());
                    }

                    writer.close();
                    System.out.println("✅ 저장 완료: " + fileName);
                    Thread.sleep(1000);

                    String insertSql = useDuplicateKeyUpdate
                        ? """
                            INSERT INTO kbo_schedule
                            (game_date, game_time, home_team, home_score, away_score, away_team, stadium, broadcast, note)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE
                                home_score = VALUES(home_score),
                                away_score = VALUES(away_score),
                                stadium = VALUES(stadium),
                                broadcast = VALUES(broadcast),
                                note = VALUES(note)
                          """
                        : """
                            INSERT IGNORE INTO kbo_schedule
                            (game_date, game_time, home_team, home_score, away_score, away_team, stadium, broadcast, note)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                          """;

                    try (
                        Connection dbConn = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
                        PreparedStatement pstmt = dbConn.prepareStatement(insertSql);
                        BufferedReader br = new BufferedReader(new FileReader(fileName))
                    ) {
                        dbConn.setAutoCommit(false);
                        String line;
                        boolean isFirst = true;
                        int inserted = 0;

                        while ((line = br.readLine()) != null) {
                            if (isFirst) { isFirst = false; continue; }
                            String[] tokens = line.split(",", -1);
                            if (tokens.length < 9) continue;

                            try {
                                String dateRaw = tokens[0].trim();
                                String dateOnly = dateRaw.replaceAll("\\s*\\([^)]*\\)", "");
                                String[] mmdd = dateOnly.split("\\.");
                                if (mmdd.length != 2) continue;

                                String m = mmdd[0].length() == 1 ? "0" + mmdd[0] : mmdd[0];
                                String d = mmdd[1].length() == 1 ? "0" + mmdd[1] : mmdd[1];
                                String fullDate = year + "-" + m + "-" + d;

                                pstmt.setDate(1, Date.valueOf(fullDate));
                                pstmt.setString(2, tokens[1].trim());
                                pstmt.setString(3, tokens[2].trim());
                                pstmt.setString(4, tokens[3].trim());
                                pstmt.setString(5, tokens[4].trim());
                                pstmt.setString(6, tokens[5].trim());
                                pstmt.setString(7, tokens[6].trim());
                                pstmt.setString(8, tokens[7].trim());
                                pstmt.setString(9, tokens[8].trim());

                                pstmt.addBatch();
                                inserted++;
                            } catch (Exception ex) {
                                System.out.println("❗ 데이터 파싱 실패: " + ex.getMessage() + " → " + line);
                            }
                        }

                        pstmt.executeBatch();
                        dbConn.commit();
                        System.out.printf("✅ [%04d-%02d] DB 저장 완료: %d건%n", year, month, inserted);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
