package com.culture.crawler.Update;

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
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.culture.util.LogUtil;
public class KBOCrowdTableCrawlerUpdate {
    public static void main(String[] args) throws Exception {
        System.setProperty("webdriver.chrome.driver", "C:\\chromedriver\\chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-allow-origins=*");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        try {
            int totalInsertCount = 0;  // 삽입된 데이터 건수를 추적
	        String latestKBODate = "20230101";
	        String latestMatchInfoDate = "20230101";

            // DB에서 마지막 KBO 경기일 가져오기
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul",
                    "root", "1234"
            )) {
	            java.sql.Statement stmt1 = conn.createStatement();
	            java.sql.ResultSet rs1 = stmt1.executeQuery("SELECT MAX(MATCH_DE) FROM colct_sports_match_info_KBO");
	            if (rs1.next() && rs1.getString(1) != null) {
	                latestKBODate = rs1.getString(1);
	            }
	            rs1.close();      
	            stmt1.close();
	            java.sql.Statement stmt2 = conn.createStatement();
	            java.sql.ResultSet rs2 = stmt2.executeQuery("SELECT MAX(MATCH_DE) FROM colct_sports_match_info WHERE GRP_NM = 'KBO'");
	            if (rs2.next() && rs2.getString(1) != null) {
	                latestMatchInfoDate = rs2.getString(1);
	            }
	            rs2.close();      
	            stmt2.close();
                System.out.println("📌 마지막 저장된 KBO 경기일: " + latestKBODate);
	            System.out.println("📌 매치 테이블 마지막 저장된 KBO 경기일: " + latestMatchInfoDate);
            } catch (Exception e) {
                System.err.println("❌ KBO 마지막 날짜 조회 실패: " + e.getMessage());
            }

            driver.get("https://www.koreabaseball.com/Record/Crowd/GraphDaily.aspx");
            driver.manage().window().maximize();

            // DB에서 마지막 경기일을 기준으로 크롤링 시작 연도 계산
	        String minDate = (latestKBODate.compareTo(latestMatchInfoDate) < 0) ? latestKBODate : latestMatchInfoDate;
	        int startYear = Integer.parseInt(minDate.substring(0, 4));
	        int endYear = LocalDate.now().getYear();
            System.out.println("📌 수집기준 시작 년도 : " + startYear);
            System.out.println("📌 수집기준 종료 년도 : " + endYear);

	        // `latestMatchDate`에 기반하여 크롤링 시작 연도 계산
            List<Map<String, String>> dataList = new ArrayList<>();  // 크롤링한 데이터를 담을 리스트

         // 크롤링할 연도 범위 설정 (스타트 연도부터 엔드 연도까지)
         for (int year = startYear; year <= endYear; year++) {
             // 연도 선택
             Select seasonSelect = new Select(wait.until(ExpectedConditions.elementToBeClickable(
                     By.id("cphContents_cphContents_cphContents_ddlSeason"))));
             seasonSelect.selectByValue(String.valueOf(year));

             // 검색 버튼 클릭
             WebElement searchBtn = wait.until(ExpectedConditions.elementToBeClickable(
                     By.id("cphContents_cphContents_cphContents_btnSearch")));
             searchBtn.click();

             // 테이블 로딩 대기 (AJAX 처리 시간 필요)
             Thread.sleep(1500);  // 또는 명확한 로딩 wait이 가능하면 교체

             // 테이블 다시 조회
             WebElement table = wait.until(ExpectedConditions.visibilityOfElementLocated(
                     By.cssSelector("#cphContents_cphContents_cphContents_udpRecord > table")));
             List<WebElement> rows = table.findElements(By.tagName("tr"));

             int yearInsertCount = 0; // 년도별 추가 건수 카운트

             // 크롤링된 데이터가 들어갈 리스트를 Map으로 처리
             for (int i = 1; i < rows.size(); i++) {
                 try {
                     List<WebElement> cols = rows.get(i).findElements(By.tagName("td"));
                     if (cols.size() < 6) continue;

                     String rawDate = cols.get(0).getText().trim();
                     LocalDate matchDate = LocalDate.parse(rawDate, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                     String matchDe = matchDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

                     // 기준일 필터링 로직 제거: 모든 데이터를 수집하려면 아래 조건을 제거합니다.
                     // if (matchDe.compareTo(latestMatchDate) <= 0) {
                     //     System.out.println("⏩ 생략: " + matchDe + " 경기");
                     //     continue;
                     // }

                     // 경기 정보 추출
                     String baseYear = matchDate.format(DateTimeFormatter.ofPattern("yyyy"));
                     String baseMt = matchDate.format(DateTimeFormatter.ofPattern("MM"));
                     String baseDay = matchDate.format(DateTimeFormatter.ofPattern("dd"));
                     String home = cols.get(2).getText().trim();
                     String away = cols.get(3).getText().trim();
                     String stadium = cols.get(4).getText().trim();
                     String crowdRaw = cols.get(5).getText().trim().replace(",", "").replace("명", "");
                     if (crowdRaw.isEmpty()) crowdRaw = "0";
                     String crowd = crowdRaw + ".00000";

                     // 데이터 맵에 기록
                     Map<String, String> matchData = new HashMap<>();
                     matchData.put("matchDe", matchDe);
                     matchData.put("baseYear", baseYear);
                     matchData.put("baseMt", baseMt);
                     matchData.put("baseDay", baseDay);
                     matchData.put("grpNm", "KBO");
                     matchData.put("leaNm", "정규리그");
                     matchData.put("homeTeam", home);
                     matchData.put("awayTeam", away);
                     matchData.put("stadium", stadium);
                     matchData.put("viewCount", crowd);

                     // 수집한 데이터 리스트에 저장
                     dataList.add(matchData);
                     // 데이터 삽입 카운트
                     totalInsertCount++;
                     yearInsertCount++;  // 해당 년도에 추가된 경기 수 증가
                 } catch (StaleElementReferenceException se) {
                     System.err.println("⚠️ StaleElement: " + i + "번째 행 건너뜀");
                     continue;
                 } catch (Exception ex) {
                     System.err.println("❌ 데이터 처리 중 오류: " + ex.getMessage());
                     continue;
                 }
             }

             System.out.println("✅ " + year + "년 경기 크롤링 완료");
             System.out.println("📅 " + year + "년 추가된 경기 수: " + yearInsertCount);
         }

         System.out.println("🎯 KBO 총 추가 건수: " + totalInsertCount);
         if (totalInsertCount > 0) {
             System.out.println("🗓️ KBO 총 추가 건수: " + totalInsertCount);
         } else {
             System.out.println("📭 추가된 경기 없음 (이미 모두 반영됨)");
         }


            // 데이터 삽입 시 마지막에 로그 기록을 한번만 하도록 수정
            int[] insertedCounts = insertKBOCrowdToDb(dataList, latestKBODate, latestMatchInfoDate);  // 각 테이블에 삽입된 데이터 건수 반환

            System.out.println("🎯 KBO 테이블에 삽입된 건수: " + insertedCounts[0]);  // KBO 테이블에 삽입된 건수
            System.out.println("🎯 MATCH_INFO 테이블에 삽입된 건수: " + insertedCounts[1]);  // MATCH_INFO 테이블에 삽입된 건수
            System.out.println("🎯 총 추가 건수: " + (insertedCounts[0] + insertedCounts[1]));  // 총 추가된 건수

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    public static int[] insertKBOCrowdToDb(
            List<Map<String, String>> dataList, String latestKBODate, String latestMatchInfoDate
    ) {
        int[] insertedCounts = new int[2];  // [0] : KBO 테이블, [1] : MATCH_INFO 테이블 삽입 건수
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        LogUtil.insertLog("KBO 스포츠 관람", "KBO 스포츠 경기 정보 수집", "colct_sports_match_info_KBO", "STARTED", null, null, null, "", today);
        LogUtil.insertLog("KBO 스포츠 관람", "KBO 스포츠 경기 정보 수집", "colct_sports_match_info", "STARTED", null, null, null, "", today);

        String groupNm = "KBO 스포츠 관람";  // 예시: 그룹명
        String jobNm = "KBO 스포츠 경기 정보 수집";  // 예시: 작업명
        String tableNm = "sports_시도별관중";  // 예시: 테이블명

        int[] countAfterDate = {0, 0};  // 기준일 이후에 삽입될 데이터 건수
        String[] tables = {"colct_sports_match_info_KBO", "colct_sports_match_info"};
        int[] counts = {0, 0};  // 각 테이블에 삽입된 데이터 건수


        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul",
                "root", "1234"
        )) {
            // 1. KBO 테이블에 삽입
            System.out.println("📌 데이터 삽입 시작 - 기준일: " + latestKBODate);
            String insertKboSql = "INSERT INTO colct_sports_match_info_KBO " +
                    "(MATCH_DE, HOME_TEAM_NM, AWAY_TEAM_NM, STDM_NM, SPORTS_VIEWNG_NMPR_CO) " +
                    "SELECT ?, ?, ?, ?, ? " +
                    "FROM DUAL " +
                    "WHERE ? >= ?";  // 기준일 이후의 데이터만 삽입하도록 WHERE 절 추가

            PreparedStatement pstmtKbo = conn.prepareStatement(insertKboSql);
	        boolean KBOSuccess = true;

	        System.out.println("📌 KBO 데이터 준비 시작");

            for (Map<String, String> matchData : dataList) {
                String matchDe = matchData.get("matchDe");
                if (matchDe.compareTo(latestKBODate) >= 0) {  // 기준일 이후만 삽입
//                    System.out.println("Batch에 추가: " + matchDe);  // 배치에 추가되는 날짜 출력
                    pstmtKbo.setString(1, matchDe);
                    pstmtKbo.setString(2, matchData.get("homeTeam"));
                    pstmtKbo.setString(3, matchData.get("awayTeam"));
                    pstmtKbo.setString(4, matchData.get("stadium"));
                    pstmtKbo.setBigDecimal(5, new java.math.BigDecimal(matchData.get("viewCount")));
                    pstmtKbo.setString(6, matchDe);  // 데이터의 날짜
                    pstmtKbo.setString(7, latestKBODate);  // 기준일
                    pstmtKbo.addBatch();  // 배치에 추가
                    counts[0]++;
                    countAfterDate[0]++;
                }
            }

	        System.out.println("📌 KBO 테이블에 삽입할 데이터 개수: " + countAfterDate[0]);

	        // KBO 테이블 삽입 후 건수 갱신
	        try {
	            int[] kLeagueBatchResults = pstmtKbo.executeBatch();  // 배치 실행 후 삽입된 건수 확인
	            insertedCounts[0] = 0;  // 삽입된 건수 초기화

	            // 배치 실행 결과를 기반으로 삽입된 건수를 계산
	            for (int result : kLeagueBatchResults) {
	                if (result != PreparedStatement.EXECUTE_FAILED) {
	                    insertedCounts[0]++;  // 삽입된 건수 증가
	                }
	            }

	            System.out.println("📌 KBO 테이블 삽입된 건수: " + insertedCounts[0]);
	            LogUtil.insertFlag(today, "colct_sports_match_info_KBO", true);

	        } catch (SQLException e) {
	            KBOSuccess = false;
	            System.err.println("❌ " + tables[0] + " 삽입 실패: " + e.getMessage());

	            // KBO 삽입 실패 로그 추가
	            LogUtil.insertLog(groupNm, "KBO 스포츠 경기 정보 수집", "colct_sports_match_info_KBO", "FAILED", countAfterDate[0], 0, 0,
	                    "KBO 삽입 실패 - Error Code: " + e.getErrorCode() + ", Message: " + e.getMessage(), today);

	            // 삽입이 실패했을 때 로그로 실패 이유 기록
	            System.err.println("📌 INSERT 실패 원인: " + e.getMessage());
	        }

	        pstmtKbo.close();

	        // KBO 테이블 삽입 로그
	        if (KBOSuccess) {
	            LogUtil.insertLog(groupNm, "KBO 스포츠 경기 정보 수집", "colct_sports_match_info_KBO", "SUCCESS", countAfterDate[0], insertedCounts[0], 0, "", today);
	        } else {
	            // 실패한 경우에도 명확히 실패 로그를 기록하도록 처리
	            LogUtil.insertLog(groupNm, "KBO 스포츠 경기 정보 수집", "colct_sports_match_info_KBO", "FAILED", countAfterDate[0], 0, 0, "KBO 삽입 실패", today);
	        }


            // 2. MATCH_INFO 테이블에 삽입
		    System.out.println("📌 K리그 데이터 삽입 시작 - 기준일: " + latestMatchInfoDate);
            String insertMatchInfoSql = "INSERT INTO colct_sports_match_info " +
                    "(MATCH_DE, BASE_YEAR, BASE_MT, BASE_DAY, GRP_NM, LEA_NM, HOME_TEAM_NM, AWAY_TEAM_NM, " +
                    "STDM_NM, SPORTS_VIEWNG_NMPR_CO, COLCT_DE, UPDT_DE) " +
                    "SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? " +
                    "FROM DUAL " +
                    "WHERE ? >= ?";  // 기준일 이후의 데이터만 삽입하도록 WHERE 절 추가

            PreparedStatement pstmtMatchInfo = conn.prepareStatement(insertMatchInfoSql);
	        boolean matchInfoSuccess = true;

	        System.out.println("📌 MATCH_INFO 데이터 준비 시작");
	        
            for (Map<String, String> matchData : dataList) {
                String matchDe = matchData.get("matchDe");
                if (matchDe.compareTo(latestMatchInfoDate) >= 0) {  // 기준일 이후만 삽입
//                    System.out.println("Batch에 추가: " + matchDe);  // 배치에 추가되는 날짜 출력
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
                    counts[1]++;
                    countAfterDate[1]++;
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
	            LogUtil.insertFlag(today, "colct_sports_match_info@KBO", true);

	        } catch (SQLException e) {
	            matchInfoSuccess = false;
	            System.err.println("❌ " + tables[1] + " 삽입 실패: " + e.getMessage());

	            // MATCH_INFO 삽입 실패 로그 추가
	            LogUtil.insertLog(groupNm, "KBO 스포츠 경기 정보 수집", "colct_sports_match_info", "FAILED", countAfterDate[1], 0, 0,
	                    "MATCH_INFO 삽입 실패 - Error Code: " + e.getErrorCode() + ", Message: " + e.getMessage(), today);

	            // 삽입이 실패했을 때 로그로 실패 이유 기록
	            System.err.println("📌 INSERT 실패 원인: " + e.getMessage());
	        }

	        pstmtMatchInfo.close();

	        // MATCH_INFO 테이블 삽입 로그
	        if (matchInfoSuccess) {
	            LogUtil.insertLog(groupNm, "KBO 스포츠 경기 정보 수집", "colct_sports_match_info", "SUCCESS", countAfterDate[1], insertedCounts[1], 0, "", today);
	        } else {
	            // 실패한 경우에도 명확히 실패 로그를 기록하도록 처리
	            LogUtil.insertLog(groupNm, "KBO 스포츠 경기 정보 수집", "colct_sports_match_info", "FAILED", countAfterDate[1], 0, 0, "MATCH_INFO 삽입 실패", today);
	        }
	    } catch (SQLException e) {
	        // 전체 삽입 실패 시 처리
	        LogUtil.insertLog(groupNm, "KBO 스포츠 경기 정보 수집", "colct_sports_match_info", "FAILED", countAfterDate[0] + countAfterDate[1], 0, 0, "전체 삽입 실패 - Error Code: " + e.getErrorCode() + ", Message: " + e.getMessage(), today);
	        System.err.println("❌ DB insert 실패: " + e.getMessage());
	    }

	    System.out.println("🎯 최종 삽입 건수: KBO = " + insertedCounts[0] + ", MATCH_INFO = " + insertedCounts[1]);

	    return insertedCounts;  // 삽입된 데이터 건수 반환
	}

}



