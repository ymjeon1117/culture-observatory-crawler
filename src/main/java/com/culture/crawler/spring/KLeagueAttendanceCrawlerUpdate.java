package com.culture.crawler.spring;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.culture.util.LogUtil;

public class KLeagueAttendanceCrawlerUpdate {
	public static void main(String[] args) {
	    System.setProperty("webdriver.chrome.driver", "C:\\chromedriver\\chromedriver.exe");

	    ChromeOptions options = new ChromeOptions();
	    options.addArguments("--remote-allow-origins=*");

	    WebDriver driver = new ChromeDriver(options);
	    WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
	    JavascriptExecutor js = (JavascriptExecutor) driver;

	    String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

	    try {
	        int totalInsertCount = 0;  // 삽입된 데이터 건수를 추적
	        String latestKleagueDate = "20230101";
	        String latestMatchInfoDate = "20230101";

	        // DB에서 마지막 K리그 경기일 가져오기
	        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
	                "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul",
	                "root", "1234"
	        )) {
	            java.sql.Statement stmt1 = conn.createStatement();
	            java.sql.ResultSet rs1 = stmt1.executeQuery("SELECT MAX(MATCH_DE) FROM colct_sports_match_info_KLEAGUE");
	            if (rs1.next() && rs1.getString(1) != null) {
	                latestKleagueDate = rs1.getString(1);
	            }
	            rs1.close();      
	            stmt1.close();
	            java.sql.Statement stmt2 = conn.createStatement();
	            java.sql.ResultSet rs2 = stmt2.executeQuery("SELECT MAX(MATCH_DE) FROM colct_sports_match_info WHERE GRP_NM = 'KLEAGUE'");
	            if (rs2.next() && rs2.getString(1) != null) {
	                latestMatchInfoDate = rs2.getString(1);
	            }
	            rs2.close();      
	            stmt2.close();
	            System.out.println("📌 K리그 테이블 마지막 저장된 K리그 경기일: " + latestKleagueDate);
	            System.out.println("📌 매치 테이블 마지막 저장된 K리그 경기일: " + latestMatchInfoDate);
	        } catch (Exception e) {
	            System.err.println("❌ K리그 마지막 날짜 조회 실패: " + e.getMessage());
	        }

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

	        // `latestMatchDate`에 기반하여 크롤링 시작 연도 계산
	        String minDate = (latestKleagueDate.compareTo(latestMatchInfoDate) < 0) ? latestKleagueDate : latestMatchInfoDate;
	        int startYear = Integer.parseInt(minDate.substring(0, 4));
	        int endYear = LocalDate.now().getYear();
            System.out.println("📌 수집기준 시작 년도 : " + startYear);
            System.out.println("📌 수집기준 종료 년도 : " + endYear);

	        List<Map<String, String>> dataList = new ArrayList<>();  // 크롤링한 데이터를 담을 리스트

	        for (int year = startYear; year <= endYear; year++) {
	            driver.switchTo().defaultContent(); driver.switchTo().frame(1);
	            new Select(wait.until(ExpectedConditions.elementToBeClickable(By.id("meetYear")))).selectByValue(String.valueOf(year));
	            js.executeScript("fnChange();");
	            Thread.sleep(2000);

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

	                    Map<String, String> matchData = new HashMap<>();
	                    matchData.put("matchDe", fullDate);
	                    matchData.put("baseYear", fullDate.substring(0, 4));
	                    matchData.put("baseMt", fullDate.substring(4, 6));
	                    matchData.put("baseDay", fullDate.substring(6, 8));
	                    matchData.put("grpNm", "KLEAGUE");
	                    matchData.put("leaNm", cells.get(2).getText());
	                    matchData.put("homeTeam", cells.get(6).getText());
	                    matchData.put("awayTeam", cells.get(7).getText());
	                    matchData.put("stadium", cells.get(9).getText());
	                    matchData.put("viewCount", cells.get(10).getText().replaceAll("[^0-9]", "") + ".00000");

	                    // 수집한 데이터 리스트에 저장
	                    dataList.add(matchData);
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
	            System.out.println("✅ " + year + "년 경기 크롤링 완료");
	        }

	        // 기준일 이후 데이터만 삽입하는 쿼리 처리
	        int[] insertedCounts = insertKLeagueToDb(dataList, latestKleagueDate, latestMatchInfoDate);  // 각 테이블에 삽입된 데이터 건수 반환

	        System.out.println("🎯 KLEAGUE 테이블에 삽입된 건수: " + insertedCounts[0]);  // KLEAGUE 테이블에 삽입된 건수
	        System.out.println("🎯 MATCH_INFO 테이블에 삽입된 건수: " + insertedCounts[1]);  // MATCH_INFO 테이블에 삽입된 건수
	        System.out.println("🎯 총 추가 건수: " + (insertedCounts[0] + insertedCounts[1]));  // 총 추가된 건수


	    } catch (Exception e) {
	        e.printStackTrace();
	    } finally {
	        driver.quit();
	    }
	}


	public static int[] insertKLeagueToDb(
	        List<Map<String, String>> dataList, String latestKleagueDate, String latestMatchInfoDate
	) {
	    int[] insertedCounts = new int[2];  // [0] : KLEAGUE 테이블, [1] : MATCH_INFO 테이블 삽입 건수
	    String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
	    LogUtil.insertLog("KLEAGUE 스포츠 관람", "KLEAGUE 스포츠 경기 정보 수집", "colct_sports_match_info_KLEAGUE", "STARTED", null, null, null, "", today);
	    LogUtil.insertLog("KLEAGUE 스포츠 관람", "KLEAGUE 스포츠 경기 정보 수집", "colct_sports_match_info", "STARTED", null, null, null, "", today);

	    int[] countAfterDate = {0, 0};  // 기준일 이후에 삽입될 데이터 건수
	    // 각 테이블에 대해 삽입 성공/실패 여부를 추적할 변수
	    String[] tables = {"colct_sports_match_info_KLEAGUE", "colct_sports_match_info"};
	    int[] counts = {0, 0};  // 각 테이블에 삽입된 데이터 건수

	    try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
	            "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul",
	            "root", "1234"
	    )) {
	    	// 0. 기준일 당일 데이터만 삭제
	    	PreparedStatement delKleagueStmt = conn.prepareStatement(
	    	    "DELETE FROM colct_sports_match_info_KLEAGUE WHERE MATCH_DE = ?"
	    	);
	    	delKleagueStmt.setString(1, latestKleagueDate);
	    	delKleagueStmt.executeUpdate();
	    	delKleagueStmt.close();

	    	PreparedStatement delMatchStmt = conn.prepareStatement(
	    	    "DELETE FROM colct_sports_match_info WHERE MATCH_DE = ? AND GRP_NM = 'KLEAGUE'"
	    	);
	    	delMatchStmt.setString(1, latestMatchInfoDate);
	    	delMatchStmt.executeUpdate();
	    	delMatchStmt.close();


	        // 1. 먼저 colct_sports_match_info_KLEAGUE 테이블에 삽입
		    System.out.println("📌 K리그 데이터 삽입 시작 - 기준일: " + latestKleagueDate);

	        String insertKleagueSql = "INSERT INTO colct_sports_match_info_KLEAGUE " +
	                "(MATCH_DE, LEA_NM, HOME_TEAM_NM, AWAY_TEAM_NM, STDM_NM, SPORTS_VIEWNG_NMPR_CO) " +
	                "SELECT ?, ?, ?, ?, ?, ? " +
	                "FROM DUAL " +
	                "WHERE ? >= ?";  // 기준일 이후의 데이터만 삽입하도록 조건 추가

	        PreparedStatement pstmtKleague = conn.prepareStatement(insertKleagueSql);
	        boolean kLeagueSuccess = true;

	        System.out.println("📌 KLEAGUE 데이터 준비 시작");

	        for (Map<String, String> matchData : dataList) {
	            String matchDe = matchData.get("matchDe");
	            if (matchDe.compareTo(latestKleagueDate) >= 0) {  // 기준일 이후만 삽입
	                pstmtKleague.setString(1, matchDe);
	                pstmtKleague.setString(2, matchData.get("leaNm"));
	                pstmtKleague.setString(3, matchData.get("homeTeam"));
	                pstmtKleague.setString(4, matchData.get("awayTeam"));
	                pstmtKleague.setString(5, matchData.get("stadium"));
	                pstmtKleague.setBigDecimal(6, new java.math.BigDecimal(matchData.get("viewCount")));
	                pstmtKleague.setString(7, matchDe);  // 데이터의 날짜
	                pstmtKleague.setString(8, latestKleagueDate);  // 기준일
	                pstmtKleague.addBatch();
	                counts[0]++;  // KLEAGUE 테이블에 삽입된 건수 증가
	                countAfterDate[0]++;  // 기준일 이후 데이터 건수 카운트
	            }
	        }

	        System.out.println("📌 KLEAGUE 테이블에 삽입할 데이터 개수: " + countAfterDate[0]);

	        // KLEAGUE 테이블 삽입 후 건수 갱신
	        try {
	            int[] kLeagueBatchResults = pstmtKleague.executeBatch();  // 배치 실행 후 삽입된 건수 확인
	            insertedCounts[0] = 0;  // 삽입된 건수 초기화

	            // 배치 실행 결과를 기반으로 삽입된 건수를 계산
	            for (int result : kLeagueBatchResults) {
	                if (result != PreparedStatement.EXECUTE_FAILED) {
	                    insertedCounts[0]++;  // 삽입된 건수 증가
	                }
	            }
	            System.out.println("📌 KLEAGUE 테이블 삽입된 건수: " + insertedCounts[0]);
	        } catch (SQLException e) {
	            kLeagueSuccess = false;
	            System.err.println("❌ " + tables[0] + " 삽입 실패: " + e.getMessage());
	            LogUtil.insertLog("KLEAGUE 스포츠 관람", "KLEAGUE 스포츠 경기 정보 수집", "colct_sports_match_info_KLEAGUE", "FAILED", countAfterDate[0], 0, 0,
	                    "KLEAGUE 삽입 실패 - Error Code: " + e.getErrorCode() + ", Message: " + e.getMessage(), today);

	            // 삽입이 실패했을 때 로그로 실패 이유 기록
	            System.err.println("📌 INSERT 실패 원인: " + e.getMessage());
	        }
	        pstmtKleague.close();

	        // KLEAGUE 테이블 삽입 로그
	        if (kLeagueSuccess) {
	            LogUtil.insertLog("KLEAGUE 스포츠 관람", "KLEAGUE 스포츠 경기 정보 수집", "colct_sports_match_info_KLEAGUE", "SUCCESS", countAfterDate[0], insertedCounts[0], 0, "", today);
	            LogUtil.insertFlag(today, "colct_sports_match_info_KLEAGUE", true);
	        } else {
	            // 실패한 경우에도 명확히 실패 로그를 기록하도록 처리
	            LogUtil.insertLog("KLEAGUE 스포츠 관람", "KLEAGUE 스포츠 경기 정보 수집", "colct_sports_match_info_KLEAGUE", "FAILED", countAfterDate[0], 0, 0, "K리그 삽입 실패", today);
	        }

	        // 2. colct_sports_match_info 테이블에 삽입

		    System.out.println("📌 매치테이블 데이터 삽입 시작 - 기준일: " + latestMatchInfoDate);

	        String insertMatchInfoSql = "INSERT INTO colct_sports_match_info " +
	                "(MATCH_DE, BASE_YEAR, BASE_MT, BASE_DAY, GRP_NM, LEA_NM, HOME_TEAM_NM, AWAY_TEAM_NM, " +
	                "STDM_NM, SPORTS_VIEWNG_NMPR_CO, COLCT_DE, UPDT_DE) " +
	                "SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? " +
	                "FROM DUAL " +
	                "WHERE ? >= ?";  // 기준일 이후의 데이터만 삽입하도록 조건 추가

	        PreparedStatement pstmtMatchInfo = conn.prepareStatement(insertMatchInfoSql);
	        boolean matchInfoSuccess = true;

	        System.out.println("📌 MATCH_INFO 데이터 준비 시작");

	        for (Map<String, String> matchData : dataList) {
	            String matchDe = matchData.get("matchDe");
	            if (matchDe.compareTo(latestMatchInfoDate) >= 0) {  // 기준일 이후만 삽입
	                pstmtMatchInfo.setString(1, matchDe);
	                pstmtMatchInfo.setString(2, matchData.get("baseYear"));
	                pstmtMatchInfo.setString(3, matchData.get("baseMt"));
	                pstmtMatchInfo.setString(4, matchData.get("baseDay"));
	                pstmtMatchInfo.setString(5, matchData.get("grpNm"));
	                pstmtMatchInfo.setString(6, matchData.get("leaNm"));
	                pstmtMatchInfo.setString(7, matchData.get("homeTeam"));
	                pstmtMatchInfo.setString(8, matchData.get("awayTeam"));
	                pstmtMatchInfo.setString(9, matchData.get("stadium"));
	                pstmtMatchInfo.setBigDecimal(10, new java.math.BigDecimal(matchData.get("viewCount")));
	                pstmtMatchInfo.setString(11, today);  // 현재 날짜
	                pstmtMatchInfo.setString(12, today);  // 현재 날짜
	                pstmtMatchInfo.setString(13, matchDe);  // 데이터의 날짜
	                pstmtMatchInfo.setString(14, latestMatchInfoDate);  // 기준일
	                pstmtMatchInfo.addBatch();
	                counts[1]++;  // MATCH_INFO 테이블에 삽입된 건수 증가
	                countAfterDate[1]++;  // 기준일 이후 데이터 건수 카운트
	            }
	        }

	        System.out.println("📌 MATCH_INFO 테이블에 삽입할 데이터 개수: " + countAfterDate[1]);

	        // MATCH_INFO 테이블 삽입 후 건수 갱신
	        try {
	            int[] matchInfoBatchResults = pstmtMatchInfo.executeBatch();  // 배치 실행 후 삽입된 건수 확인
	            insertedCounts[1] = 0;  // 삽입된 건수 초기화

	            // 배치 실행 결과를 기반으로 삽입된 건수를 계산
	            for (int result : matchInfoBatchResults) {
	                if (result != PreparedStatement.EXECUTE_FAILED) {
	                    insertedCounts[1]++;  // 삽입된 건수 증가
	                }
	            }
	            System.out.println("📌 MATCH_INFO 테이블 삽입된 건수: " + insertedCounts[1]);
	        } catch (SQLException e) {
	            matchInfoSuccess = false;
	            System.err.println("❌ " + tables[1] + " 삽입 실패: " + e.getMessage());
	            LogUtil.insertLog("KLEAGUE 스포츠 관람", "KLEAGUE 스포츠 경기 정보 수집", "colct_sports_match_info", "FAILED", countAfterDate[1], 0, 0,
	                    "MATCH_INFO 삽입 실패 - Error Code: " + e.getErrorCode() + ", Message: " + e.getMessage(), today);
	            // 삽입이 실패했을 때 로그로 실패 이유 기록
	            System.err.println("📌 INSERT 실패 원인: " + e.getMessage());
	        }

	        pstmtMatchInfo.close();

	        // MATCH_INFO 테이블 삽입 로그
	        if (matchInfoSuccess) {
	            LogUtil.insertLog("KLEAGUE 스포츠 관람", "KLEAGUE 스포츠 경기 정보 수집", "colct_sports_match_info", "SUCCESS", countAfterDate[1], insertedCounts[1], 0, "", today);
	            LogUtil.insertFlag(today, "colct_sports_match_info@KLEAGUE", true);
	        } else {
	            // 실패한 경우에도 명확히 실패 로그를 기록하도록 처리
	            LogUtil.insertLog("KLEAGUE 스포츠 관람", "KLEAGUE 스포츠 경기 정보 수집", "colct_sports_match_info", "FAILED", countAfterDate[1], 0, 0, "MATCH_INFO 삽입 실패", today);
	        }

	    } catch (SQLException e) {
	        // 전체 삽입 실패 시 처리
	        LogUtil.insertLog("KLEAGUE 스포츠 관람", "KLEAGUE 스포츠 경기 정보 수집", "colct_sports_match_info", "FAILED", countAfterDate[0] + countAfterDate[1], 0, 0, "전체 삽입 실패 - Error Code: " + e.getErrorCode() + ", Message: " + e.getMessage(), today);
	        System.err.println("❌ DB insert 실패: " + e.getMessage());
	    }

	    System.out.println("🎯 최종 삽입 건수: KLEAGUE = " + insertedCounts[0] + ", MATCH_INFO = " + insertedCounts[1]);

	    return insertedCounts;  // 삽입된 데이터 건수 반환
	}


}
