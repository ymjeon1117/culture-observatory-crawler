package com.culture.crawler.CR;

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

public class KLeagueAttendanceCrawler {
    public static void main(String[] args) {
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver\\chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        int startYear = 2023;
        int endYear = 2025;
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        try {
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
            for (int year = startYear; year <= endYear; year++) {
                driver.switchTo().defaultContent(); driver.switchTo().frame(1);
                new Select(wait.until(ExpectedConditions.elementToBeClickable(By.id("meetYear")))).selectByValue(String.valueOf(year));
                js.executeScript("fnChange();");
                Thread.sleep(2000);

                String csvFile = "kleague_matchinfo_" + year + ".csv";
                PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(csvFile, false)));

                // ✅ 12개 컬럼 헤더 (MATCH_SEQ_NO 제외)
                writer.println("MATCH_DE,BASE_YEAR,BASE_MT,BASE_DAY,GRP_NM,LEA_NM,HOME_TEAM_NM,AWAY_TEAM_NM,STDM_NM,SPORTS_VIEWNG_NMPR_CO,COLCT_DE,UPDT_DE");

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


                        String baseYear = fullDate.substring(0, 4);
                        String baseMt = fullDate.substring(4, 6);
                        String baseDay = fullDate.substring(6, 8);
                        String grpNm = "KLEAGUE";
//                        String leaNm = cells.get(2).getText().contains("K리그1") ? "K LEAGUE 1" :
//                                       cells.get(2).getText().contains("K리그2") ? "K LEAGUE 2" : "KLEAGUE";
                        String leaNm = cells.get(2).getText();
                        String homeTeam = cells.get(6).getText();
                        String awayTeam = cells.get(7).getText();
                        String stadium = cells.get(9).getText();
                        String viewCount = cells.get(10).getText().replaceAll("[^0-9]", "") + ".00000";

                        String line = String.join(",",
                                fullDate, baseYear, baseMt, baseDay, grpNm, leaNm,
                                homeTeam, awayTeam, stadium, viewCount, today, today
                        );
                        writer.println(line);
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

                writer.close();
                System.out.println("✅ " + year + "년 CSV 저장 완료: " + csvFile);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }
}

