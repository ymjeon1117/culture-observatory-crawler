package com.culture.crawler;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

public class KBOGameScheduleCrawler {
    public static void main(String[] args) {
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver\\chromedriver.exe");
        System.out.println("작업 디렉터리: " + System.getProperty("user.dir"));

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        try {
            driver.get("https://www.koreabaseball.com/Schedule/Schedule.aspx#");
            driver.manage().window().maximize();
            System.out.println("✅ 사이트 접속 및 창 최대화 완료");

            LocalDate now = LocalDate.now();
            int currentYear = now.getYear();
            int currentMonth = now.getMonthValue();

            WebElement yearSelectElem = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("ddlYear")));
            Select yearSelect = new Select(yearSelectElem);

            WebElement monthSelectElem = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("ddlMonth")));
            Select monthSelect = new Select(monthSelectElem);

            yearSelect.selectByValue(String.valueOf(currentYear));
            Thread.sleep(1000);

            for (int month = 1; month <= currentMonth; month++) {
                String monthStr = (month < 10) ? "0" + month : String.valueOf(month);
                monthSelect.selectByValue(monthStr);
                System.out.println("✅ " + currentYear + "년 " + monthStr + "월 선택");
                Thread.sleep(3000);

                String csvFile = String.format("kbo_schedule_%04d%02d.csv", currentYear, month);
                PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(csvFile, false)));
                writer.println("일자,요일,경기시간,홈팀,VS,원정팀,구장,중계");

                WebElement scheduleTable = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("tblScheduleList")));
                List<WebElement> rows = scheduleTable.findElements(By.cssSelector("tbody > tr"));
                System.out.println("📄 " + currentYear + "년 " + monthStr + "월 일정 행 개수: " + rows.size());

                String currentDate = "";

                for (WebElement row : rows) {
                    List<WebElement> cols = row.findElements(By.tagName("td"));
                    StringBuilder line = new StringBuilder();

                    int offset = 0;

                    if (cols.size() == 9) {
                        // 날짜 포함된 경우
                        currentDate = cols.get(0).getText().trim();
                    } else {
                        // 날짜 생략 → 이전 날짜 유지
                        offset = 1;
                    }

                    line.append(currentDate).append(",");

                    // 나머지 컬럼 정리
                    for (int i = offset; i < cols.size(); i++) {
                        String text = cols.get(i).getText().trim();
                        text = text.replaceAll("\\r?\\n", "; "); // 줄바꿈 제거
                        if (text.contains(",")) {
                            text = "\"" + text + "\"";
                        }
                        line.append(text);
                        if (i < cols.size() - 1) {
                            line.append(",");
                        }
                    }

                    // 누락된 열이 있다면 빈 컬럼 추가 (최소 7개)
                    int actualCols = cols.size() - offset;
                    while (actualCols < 7) {
                        line.append(",");
                        actualCols++;
                    }

                    writer.println(line.toString());
                }

                writer.close();
                System.out.println("✅ CSV 저장 완료: " + csvFile);
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // driver.quit(); // 필요 시 주석 해제
        }
    }
}
