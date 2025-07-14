package com.culture.crawler;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.Duration;
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

        try {
            driver.get("https://www.koreabaseball.com/Schedule/Schedule.aspx#");
            driver.manage().window().maximize();

            // ✅ 설정: 수집 범위 지정
            int startYear = 2024;
            int startMonth = 1;
            int endYear = 2025;
            int endMonth = 7;

            WebElement yearSelectElem = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("ddlYear")));
            WebElement monthSelectElem = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("ddlMonth")));
            Select yearSelect = new Select(yearSelectElem);
            Select monthSelect = new Select(monthSelectElem);

            for (int year = startYear; year <= endYear; year++) {
                int from = (year == startYear) ? startMonth : 1;
                int to = (year == endYear) ? endMonth : 12;

                yearSelect.selectByValue(String.valueOf(year));
                Thread.sleep(1000);

                for (int month = from; month <= to; month++) {
                    String monthStr = (month < 10) ? "0" + month : String.valueOf(month);
                    monthSelect.selectByValue(monthStr);
                    Thread.sleep(3000);

                    String fileName = String.format("kbo_schedule_%04d%02d.csv", year, month);
                    PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(fileName, false)));
                    writer.println("일자,시간,홈팀,홈점수,VS,원정점수,원정팀,구장,중계,비고");

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

                            // 경기 정보
                            String homeTeam = "", awayTeam = "", homeScore = "", awayScore = "", vs = "vs";
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

                            String line = String.join(",", currentDate, time, homeTeam, homeScore, vs, awayScore, awayTeam, stadium, tvText, etc);
                            writer.println(line);
                        }

                    } catch (Exception e) {
                        System.out.println("[WARN] 테이블 파싱 실패: " + e.getMessage());
                        // 빈 파일이더라도 헤더는 있음
                    }

                    writer.close();
                    System.out.println("✅ 저장 완료: " + fileName);
                    Thread.sleep(1000);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}
