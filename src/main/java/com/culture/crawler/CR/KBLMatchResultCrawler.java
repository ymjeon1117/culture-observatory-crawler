package com.culture.crawler.CR;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class KBLMatchResultCrawler {
    public static void main(String[] args) throws Exception {
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver\\chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            driver.get("https://www.kbl.or.kr/match/schedule?type=SCHEDULE");
            driver.manage().window().maximize();
            Thread.sleep(3000);

            int startYear = 2024;
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

    private static void extractAndAppendToCsv(WebDriver driver, PrintWriter writer, String today) {
        List<WebElement> gameLists = driver.findElements(By.cssSelector(".cont-box .game-schedule-list"));

        for (WebElement gameList : gameLists) {
            String dateText = gameList.findElement(By.tagName("p")).getText().trim(); // ex: 06.01
            String fullDate = "2024-" + dateText.replace(".", "").replaceFirst("(\\d{2})(\\d{2})", "$1-$2");
            String matchDe = fullDate.replace("-", "");
            String baseYear = matchDe.substring(0, 4);
            String baseMonth = matchDe.substring(4, 6);
            String baseDay = matchDe.substring(6, 8);

            List<WebElement> items = gameList.findElements(By.cssSelector("ul > li"));
            for (WebElement item : items) {
                if (item.findElements(By.cssSelector("div.sub")).isEmpty()) continue;

                try {
                    WebElement desc = item.findElement(By.cssSelector("div.sub > div.desc"));
                    String gameType = desc.findElement(By.cssSelector("span.label")).getText().trim(); // ← 경기종류
                    String location = desc.findElements(By.cssSelector("ul > li")).get(1).getText().trim();

                    List<WebElement> teams = item.findElements(By.cssSelector("div.info ul.versus > li"));
                    if (teams.size() != 2) continue;

                    String homeTeam = teams.get(0).findElements(By.tagName("p")).get(0).getText().trim();
                    String awayTeam = teams.get(1).findElements(By.tagName("p")).get(0).getText().trim();

                    writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                            matchDe, baseYear, baseMonth, baseDay,
                            "KBL", gameType,         // ← GRP_NM은 KBL 고정, LEA_NM은 경기종류
                            homeTeam, awayTeam, location,
                            "0.00000", today, today);


                    System.out.println("✅ " + matchDe + " [" + gameType + "] " + homeTeam + " vs " + awayTeam);

                } catch (Exception e) {
                    System.out.println("[WARN] 파싱 실패: " + e.getMessage());
                }
            }
        }
    }

}
