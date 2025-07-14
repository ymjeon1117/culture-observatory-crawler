package com.culture.crawler;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.*;

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

         // 🔸 시작/끝 범위 정의
            int startYear = 2024;
            int startMonth = 1;
            int endYear = 2025;
            int endMonth = 7;

            // 🔸 월 리스트 생성 (역순)
            List<String> targetMonths = new ArrayList<>();
            for (int year = endYear; year >= startYear; year--) {
                int from = (year == endYear) ? endMonth : 12;
                int to = (year == startYear) ? startMonth : 1;
                for (int month = from; month >= to; month--) {
                    targetMonths.add(String.format("%d. %02d", year, month));
                }
            }


            for (String targetMonth : targetMonths) {
                // 날짜 영역이 일치할 때까지 이전 버튼 클릭
                while (true) {
                    WebElement dateElem = driver.findElement(By.cssSelector(".filter-wrap ul.date li:nth-child(2) p"));
                    String currentMonth = dateElem.getText().trim();
                    if (currentMonth.equals(targetMonth)) break;

                    WebElement prevBtn = driver.findElement(By.cssSelector(".filter-wrap ul.date li:nth-child(1) button"));
                    prevBtn.click();
                    Thread.sleep(2000);
                }

                System.out.println("[INFO] 수집 중: " + targetMonth);
                extractAndSaveCurrentMonth(driver, targetMonth);
            }

        } finally {
            driver.quit();
        }
    }

    private static void extractAndSaveCurrentMonth(WebDriver driver, String yearMonth) throws Exception {
        String ymKey = yearMonth.replace(".", "").replace(" ", "");
        String fileName = "kbl_schedule_" + ymKey + ".csv";
        PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(fileName, false)));
        writer.println("날짜,시간,경기종류,장소,홈팀,홈점수,원정팀,원정점수,중계채널");

        List<WebElement> gameLists = driver.findElements(By.cssSelector(".cont-box .game-schedule-list"));

        for (WebElement gameList : gameLists) {
            String dateText = gameList.findElement(By.tagName("p")).getText().trim(); // ex) 01.02
            String fullDate = "2025-" + dateText.replace(".", "").replaceFirst("(\\d{2})(\\d{2})", "$1-$2");

            List<WebElement> items = gameList.findElements(By.cssSelector("ul > li"));
            for (WebElement item : items) {
            	if (item.findElements(By.cssSelector("div.sub")).isEmpty()) {
            	    continue; // 날짜줄 또는 경기 아님
            	}
                try {
                    WebElement desc = item.findElement(By.cssSelector("div.sub > div.desc"));
                    String gameType = desc.findElement(By.cssSelector("span.label")).getText().trim();
                    List<WebElement> descInfo = desc.findElements(By.cssSelector("ul > li"));
                    String time = descInfo.get(0).getText().trim();
                    String location = descInfo.get(1).getText().trim();

                    List<WebElement> channels = item.findElements(By.cssSelector("div.sub > div.channel img"));
                    String channelText = channels.stream().map(e -> {
                        String src = e.getAttribute("src");
                        if (src.contains("T36")) return "tvN SPORTS";
                        else if (src.contains("T21")) return "IB SPORTS";
                        else if (src.contains("tving")) return "TVING";
                        else return "기타";
                    }).collect(Collectors.joining(", "));

                    List<WebElement> teams = item.findElements(By.cssSelector("div.info ul.versus > li"));
                    if (teams.size() != 2) continue;

                    String team1 = teams.get(0).findElements(By.tagName("p")).get(0).getText().trim();
                    String score1 = teams.get(0).findElements(By.tagName("p")).get(1).getText().trim();
                    String team2 = teams.get(1).findElements(By.tagName("p")).get(0).getText().trim();
                    String score2 = teams.get(1).findElements(By.tagName("p")).get(1).getText().trim();

                    writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,\"%s\"%n",
                        fullDate, time, gameType, location, team1, score1, team2, score2, channelText);

                } catch (Exception e) {
                    System.out.println("[WARN] 항목 파싱 실패: " + e.getMessage());
                }
            }
        }

        writer.close();
        System.out.println("[OK] 저장 완료: " + fileName);
    }
}
