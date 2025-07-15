package com.culture.crawler;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class KBOCrowdTableCrawlerRaw {
    public static void main(String[] args) throws Exception {
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver\\chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            driver.get("https://www.koreabaseball.com/Record/Crowd/GraphDaily.aspx");
            driver.manage().window().maximize();

            WebElement table = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("#cphContents_cphContents_cphContents_udpRecord > table")));

            List<WebElement> rows = table.findElements(By.tagName("tr"));

            PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter("kbo_daily_crowd.csv", false)));
            writer.println("날짜,요일,홈,방문,구장,관중수");

            for (int i = 1; i < rows.size(); i++) {
                List<WebElement> cols = rows.get(i).findElements(By.tagName("td"));
                if (cols.size() == 6) {
                    String date = cols.get(0).getText().trim();
                    String day = cols.get(1).getText().trim();
                    String home = cols.get(2).getText().trim();
                    String away = cols.get(3).getText().trim();
                    String stadium = cols.get(4).getText().trim();
                    String crowd = cols.get(5).getText().trim().replace(",", "");
                    writer.printf("%s,%s,%s,%s,%s,%s%n", date, day, home, away, stadium, crowd);
                }
            }

            writer.close();
            System.out.println("관중수 데이터 저장 완료.");
        } finally {
            driver.quit();
        }
    }
}
