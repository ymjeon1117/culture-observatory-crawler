package com.culture.crawler;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

public class KLeagueAttendanceCrawlerRaw {
    public static void main(String[] args) {
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver\\chromedriver.exe");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");
        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        int startYear = 2023;
        int endYear = 2025;

        try {
            driver.get("https://data.kleague.com/");
            driver.manage().window().maximize();
            System.out.println("✅ 사이트 접속 완료");

            driver.switchTo().frame(1);
            WebElement dataCenterMenu = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("body > div.wrap > div > div > div.headerLeft > div.main-menu > ul > li:nth-child(1) > a")));
            js.executeScript("arguments[0].click();", dataCenterMenu);
            System.out.println("✅ 데이터센터 클릭 성공");

            driver.switchTo().defaultContent();
            driver.switchTo().frame(1);

            WebElement subMenu = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("body > div.wrap > div.sub-menu > div > ul > li:nth-child(5) > a")));
            subMenu.click();
            System.out.println("✅ 서브 메뉴 클릭 성공");

            driver.switchTo().defaultContent();
            driver.switchTo().frame(1);
            WebElement menu1 = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("#subMenuLayer_0016 > li > a:nth-child(6)")));
            menu1.click();
            System.out.println("✅ 메뉴1 클릭 완료");
            Thread.sleep(1000);

            driver.switchTo().defaultContent();
            driver.switchTo().frame(1);
            WebElement menu2 = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//*[@id='subMenuLayer_0029']/li/a[7]")));
            menu2.click();
            System.out.println("✅ 메뉴2 클릭 완료");
            Thread.sleep(1000);

            driver.switchTo().defaultContent();
            driver.switchTo().frame(1);
            WebElement menu3 = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//*[@id='subMenuLayer_0492']/li/a[1]")));
            menu3.click();
            System.out.println("✅ 메뉴3 클릭 완료");
            Thread.sleep(1000);

            driver.switchTo().defaultContent();
            driver.switchTo().frame(1);
            By tableBy = By.cssSelector("#commonview > div.searchDataset.sub-team-table table#tableCrowd01");
            wait.until(ExpectedConditions.visibilityOfElementLocated(tableBy));

            WebElement pageCount = wait.until(ExpectedConditions.elementToBeClickable(By.id("pageViewCount")));
            Select viewCountSelect = new Select(pageCount);
            viewCountSelect.selectByValue("500");
            js.executeScript("fnChange();");
            System.out.println("✅ 500건 보기 설정 완료");
            Thread.sleep(2000);

            for (int year = startYear; year <= endYear; year++) {
                driver.switchTo().defaultContent();
                driver.switchTo().frame(1);

                Select yearSelect = new Select(wait.until(ExpectedConditions.elementToBeClickable(By.id("meetYear"))));
                yearSelect.selectByValue(String.valueOf(year));
                js.executeScript("fnChange();");
                System.out.println("✅ 연도 선택 완료: " + year);
                Thread.sleep(2000);

                String csvFile = "kleague_full_table_" + year + ".csv";
                PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(csvFile, false)));
                writer.println("순위,시즌,대회,대회명,경기번호,일자,홈팀,상대,경기결과,경기장,관중수,원정팀,비고");

                By paginationUlBy = By.cssSelector("#commonview > div.pagination > ul");
                int currentPageIndex = 0;
                boolean hasNextPage = true;

                while (hasNextPage) {
                    driver.switchTo().defaultContent();
                    driver.switchTo().frame(1);

                    WebElement table = wait.until(ExpectedConditions.visibilityOfElementLocated(tableBy));
                    List<WebElement> rows = table.findElements(By.cssSelector("tbody > tr"));
                    System.out.println("📄 " + year + "년 - 페이지 " + (currentPageIndex + 1) + " / 행 수: " + rows.size());

                    for (WebElement row : rows) {
                        List<WebElement> cells = row.findElements(By.tagName("td"));
                        if (cells.size() < 13) continue;

                        StringBuilder line = new StringBuilder();
                        for (int i = 0; i < cells.size(); i++) {
                            String text = cells.get(i).getText().trim();
                            if (text.contains(",")) text = "\"" + text + "\"";
                            line.append(text);
                            if (i < cells.size() - 1) line.append(",");
                        }
                        writer.println(line.toString());
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
