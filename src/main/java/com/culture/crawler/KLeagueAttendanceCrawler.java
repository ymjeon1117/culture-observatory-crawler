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
import org.openqa.selenium.support.ui.WebDriverWait;

public class KLeagueAttendanceCrawler {
    public static void main(String[] args) {
        // 크롬 드라이버 경로
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver\\chromedriver.exe");
        System.out.println("작업 디렉터리: " + System.getProperty("user.dir"));
        // 크롬 옵션 설정
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");

        WebDriver driver = new ChromeDriver(options);

        try {
            // 1. 기본 페이지 접속
            driver.get("https://data.kleague.com/");
            System.out.println("✅ 사이트 접속 완료");

            // 창 최대화
            driver.manage().window().maximize();
            System.out.println("✅ 창 최대화 완료");

            // 명시적 대기 객체, 최대 20초
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            // 2. 첫 번째 프레임(인덱스 1) 진입
            driver.switchTo().frame(1);
            System.out.println("✅ 첫번째 프레임(1) 진입, URL: " + driver.getCurrentUrl());

            // 3. 데이터센터 메뉴 대기 및 클릭
            By dataCenterSelector = By.cssSelector("body > div.wrap > div > div > div.headerLeft > div.main-menu > ul > li:nth-child(1) > a");
            WebElement dataCenterMenu = wait.until(ExpectedConditions.elementToBeClickable(dataCenterSelector));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", dataCenterMenu);
            System.out.println("✅ 데이터센터 클릭 성공");

            // 4. 프레임 최상위로 빠져나오기
            driver.switchTo().defaultContent();

            // 5. 다시 첫 번째 프레임 진입 (데이터센터 클릭 후 페이지 반영 대기)
            driver.switchTo().frame(1);
            System.out.println("✅ 데이터센터 진입 후 프레임(1) 재진입, URL: " + driver.getCurrentUrl());

            // 6. 서브 메뉴 대기 및 클릭 (예시: 5번째 메뉴)
            driver.switchTo().defaultContent();
            driver.switchTo().frame(1);
            System.out.println("✅ 서브메뉴 클릭 전 프레임(1) 재진입");
            By subMenuSelector = By.cssSelector("body > div.wrap > div.sub-menu > div > ul > li:nth-child(5) > a");
            WebElement subMenu = wait.until(ExpectedConditions.elementToBeClickable(subMenuSelector));
            subMenu.click();
            System.out.println("✅ 서브 메뉴 클릭 성공");

            // 7. 추가 메뉴 클릭 - 항상 프레임 재진입 및 로그 출력

            // 메뉴1
            driver.switchTo().defaultContent();
            driver.switchTo().frame(1);
            System.out.println("✅ 메뉴1 클릭 전 프레임(1) 재진입");
            WebElement menu1 = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("#subMenuLayer_0016 > li > a:nth-child(6)")));
            menu1.click();
            System.out.println("✅ 메뉴1 클릭 완료");
            Thread.sleep(1000);

         // 메뉴2
            driver.switchTo().defaultContent();
            driver.switchTo().frame(1);
            System.out.println("✅ 메뉴2 클릭 전 프레임(1) 재진입");
            WebElement menu2 = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@id='subMenuLayer_0029']/li/a[7]")));
            menu2.click();
            System.out.println("✅ 메뉴2 클릭 완료");
            Thread.sleep(1000);

            // 메뉴3
            driver.switchTo().defaultContent();
            driver.switchTo().frame(1);
            System.out.println("✅ 메뉴3 클릭 전 프레임(1) 재진입");
            WebElement menu3 = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@id='subMenuLayer_0492']/li/a[1]")));
            menu3.click();
            System.out.println("✅ 메뉴3 클릭 완료");
            Thread.sleep(1000);

            System.out.println("✅ 모든 추가 메뉴 처리 완료");

         // 테이블 선택자 (CSS)
            By tableBy = By.cssSelector("#commonview > div.searchDataset.sub-team-table table#tableCrowd01");

            // CSV 출력 준비
            String csvFile = "kleague_full_table.csv";
            PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(csvFile, false)));

            // 헤더는 직접 써주거나, 첫 행에서 추출해도 됨
            writer.println("순위,시즌,대회,대회명,경기번호,일자,홈팀,상대,경기결과,경기장,관중수,원정팀,비고");

            // 페이지 네비게이션 ul 선택자
            By paginationUlBy = By.cssSelector("#commonview > div.pagination > ul");

            // 현재 페이지 인덱스 (0부터 시작)
            int currentPageIndex = 0;

            boolean hasNextPage = true;

            while (hasNextPage) {
                // 프레임 재진입 (페이지 로딩 후 항상 프레임 상태 재설정)
                driver.switchTo().defaultContent();
                driver.switchTo().frame(1);

                // 테이블 로딩 대기 (충분한 시간 확보 위해 대기 시간 늘리기 또는 커스텀 대기 추가)
                WebElement table = wait.until(ExpectedConditions.visibilityOfElementLocated(tableBy));

                // 행 추출
                List<WebElement> rows = table.findElements(By.cssSelector("tbody > tr"));
                System.out.println("페이지 " + (currentPageIndex + 1) + " 행 개수: " + rows.size());

                for (WebElement row : rows) {
                    List<WebElement> cells = row.findElements(By.tagName("td"));
                    StringBuilder line = new StringBuilder();

                    for (int i = 0; i < cells.size(); i++) {
                        String text = cells.get(i).getText().trim();
                        if (text.contains(",")) {
                            text = "\"" + text + "\"";  // CSV에서 쉼표 포함 시 큰따옴표 처리
                        }
                        line.append(text);
                        if (i < cells.size() - 1) {
                            line.append(",");
                        }
                    }
                    writer.println(line.toString());
                }

                try {
                    // 페이지 네비게이션 ul 및 li 목록 다시 찾기
                    WebElement paginationUl = wait.until(ExpectedConditions.visibilityOfElementLocated(paginationUlBy));
                    List<WebElement> pageLinks = paginationUl.findElements(By.tagName("li"));

                    // 현재 페이지가 마지막 페이지인지 체크
                    if (currentPageIndex >= pageLinks.size() - 1) {
                        hasNextPage = false;
                        System.out.println("마지막 페이지 도달, 종료");
                    } else {
                        // 다음 페이지 li 클릭 전 스크롤 이동
                        WebElement nextPageLi = pageLinks.get(currentPageIndex + 1);
                        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", nextPageLi);

                        nextPageLi.click();
                        System.out.println("다음 페이지 클릭: " + (currentPageIndex + 2));
                        currentPageIndex++;

                        Thread.sleep(5000);  // 페이지 전환 대기, 충분히 길게
                    }
                } catch (Exception e) {
                    hasNextPage = false;
                    System.out.println("페이지 이동 중 오류 발생, 종료");
                    e.printStackTrace();
                }
            }

            writer.close();
            System.out.println("✅ 모든 페이지 크롤링 및 CSV 저장 완료: " + csvFile);



        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 실행 후 종료 여부 결정
            // driver.quit();
        }
    }
}
