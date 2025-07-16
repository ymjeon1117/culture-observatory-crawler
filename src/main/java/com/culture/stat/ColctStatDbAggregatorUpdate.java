package com.culture.stat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ColctStatDbAggregatorUpdate {

    static class Stat {
        String baseDe, cityName, cityCode;
        double klea = 0, kbo = 0, kbl = 0, wkbl = 0, kovo = 0;
        int kleaCount = 0, kboCount = 0, kblCount = 0, wkblCount = 0, kovoCount = 0;
        String colctDe = "", updtDe = "";

     // setPreparedStatement 메소드에서 파라미터 개수 수정
        public void setPreparedStatement(PreparedStatement pstmt) throws SQLException {
            double totalView = klea + kbo + kbl + wkbl + kovo;
            int totalCnt = kleaCount + kboCount + kblCount + wkblCount + kovoCount;

            pstmt.setString(1, baseDe);  // BASE_DE (문자열로 전달)
            pstmt.setString(2, baseDe.substring(0, 4));  // BASE_YEAR
            pstmt.setString(3, baseDe.substring(4, 6));  // BASE_MT
            pstmt.setString(4, baseDe.substring(6, 8));  // BASE_DAY
            pstmt.setString(5, cityCode);  // CTPRVN_CD
            pstmt.setString(6, cityName);  // CTPRVN_NM
            pstmt.setDouble(7, klea);  // KLEA_VIEWNG_NMPR_CO
            pstmt.setDouble(8, kbo);  // KBO_VIEWNG_NMPR_CO
            pstmt.setDouble(9, kbl);  // KBL_VIEWNG_NMPR_CO
            pstmt.setDouble(10, wkbl);  // WKBL_VIEWNG_NMPR_CO
            pstmt.setDouble(11, kovo);  // KOVO_VIEWNG_NMPR_CO
            pstmt.setDouble(12, totalView);  // SPORTS_VIEWNG_NMPR_CO
            pstmt.setInt(13, kleaCount);  // KLEA_MATCH_CO
            pstmt.setInt(14, kboCount);  // KBO_MATCH_CO
            pstmt.setInt(15, kblCount);  // KBL_MATCH_CO
            pstmt.setInt(16, wkblCount);  // WKBL_MATCH_CO
            pstmt.setInt(17, kovoCount);  // KOVO_MATCH_CO
            pstmt.setInt(18, totalCnt);  // SPORTS_MATCH_CO
            pstmt.setString(19, colctDe);  // COLCT_DE
            pstmt.setString(20, updtDe);  // UPDT_DE
        }

    }

    static final Map<String, String> stdmToCityName = Map.ofEntries(Map.entry("강릉하이원아레나", "강원"),
    		Map.entry("원주종합체육관", "강원"),
    		Map.entry("춘천 송암", "강원"),
    		Map.entry("고양 소노 아레나", "경기"),
    		Map.entry("고양체육관", "경기"),
    		Map.entry("김포솔터축구장", "경기"),
    		Map.entry("부천 종합", "경기"),
    		Map.entry("수원", "경기"),
    		Map.entry("수원 KT 아레나", "경기"),
    		Map.entry("수원 월드컵", "경기"),
    		Map.entry("수원 종합", "경기"),
    		Map.entry("안산 와스타디움", "경기"),
    		Map.entry("안양 정관장 아레나", "경기"),
    		Map.entry("안양 종합", "경기"),
    		Map.entry("안양실내체육관", "경기"),
    		Map.entry("용인 미르", "경기"),
    		Map.entry("이천 LG 챔피언스파크", "경기"),
    		Map.entry("탄천 종합", "경기"),
    		Map.entry("화성 종합", "경기"),
    		Map.entry("양산 종합", "경남"),
    		Map.entry("창원", "경남"),
    		Map.entry("창원 축구센터", "경남"),
    		Map.entry("창원체육관", "경남"),
    		Map.entry("김천 종합", "경북"),
    		Map.entry("포항", "경북"),
    		Map.entry("포항 스틸야드", "경북"),
    		Map.entry("광주", "광주"),
    		Map.entry("광주 월드컵", "광주"),
    		Map.entry("광주 전용", "광주"),
    		Map.entry("대구", "대구"),
    		Map.entry("대구iM뱅크PARK", "대구"),
    		Map.entry("대구체육관", "대구"),
    		Map.entry("대전", "대전"),
    		Map.entry("대전 월드컵", "대전"),
    		Map.entry("대전(신)", "대전"),
    		Map.entry("한밭", "대전"),
    		Map.entry("부산 구덕", "부산"),
    		Map.entry("부산 아시아드", "부산"),
    		Map.entry("부산사직체육관", "부산"),
    		Map.entry("사직", "부산"),
    		Map.entry("고척", "서울"),
    		Map.entry("목동 종합", "서울"),
    		Map.entry("서울 월드컵", "서울"),
    		Map.entry("잠실", "서울"),
    		Map.entry("잠실실내체육관", "서울"),
    		Map.entry("잠실학생체육관", "서울"),
    		Map.entry("울산", "울산"),
    		Map.entry("울산 문수", "울산"),
    		Map.entry("울산 종합", "울산"),
    		Map.entry("울산동천체육관", "울산"),
    		Map.entry("문학", "인천"),
    		Map.entry("인천 전용", "인천"),
    		Map.entry("광양 전용", "전남"),
    		Map.entry("여수", "전남"),
    		Map.entry("군산월명체육관", "전북"),
    		Map.entry("전주 월드컵", "전북"),
    		Map.entry("전주실내체육관", "전북"),
    		Map.entry("제주 월드컵", "제주"),
    		Map.entry("아산 이순신", "충남"),
    		Map.entry("천안 종합", "충남"),
    		Map.entry("제천체육관", "충북"),
    		Map.entry("청주", "충북"),
    		Map.entry("청주 종합", "충북")
    );

    static final Map<String, String> cityNameToCode = Map.ofEntries(
        Map.entry("서울", "11"), Map.entry("부산", "26"), Map.entry("대구", "27"), Map.entry("인천", "28"),
        Map.entry("광주", "29"), Map.entry("대전", "30"), Map.entry("울산", "31"), Map.entry("세종", "36"),
        Map.entry("경기", "41"), Map.entry("강원", "42"), Map.entry("충북", "43"), Map.entry("충남", "44"),
        Map.entry("전북", "45"), Map.entry("전남", "46"), Map.entry("경북", "47"), Map.entry("경남", "48"), Map.entry("제주", "50")
    );
    
    public static void main(String[] args) throws Exception {
        String jdbcUrl = "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8";
        String user = "root";
        String password = "1234";

        Map<String, Stat> map = new LinkedHashMap<>();
        Set<String> uniqueMatchSeqSet = new HashSet<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
        	// 1. 마지막 날짜 조회 (기준일: 가장 최근 BASE_DE)
        	String lastDateQuery = "SELECT MAX(BASE_DE) FROM colct_sports_viewng_info_stat";
        	LocalDate lastDateInDb = null;

        	try (PreparedStatement stmt = conn.prepareStatement(lastDateQuery);
        	     ResultSet rs = stmt.executeQuery()) {
        	    if (rs.next()) {
        	        String lastDateStr = rs.getString(1);
        	        if (lastDateStr != null) {
        	            try {
        	                // 로그 추가: 날짜 문자열과 처리 전에 출력
        	                System.out.println("🔍 날짜 문자열: " + lastDateStr);

        	                // 여기서 'yyyy-MM-dd' 형식의 날짜를 처리하는 포맷터 사용
        	                lastDateInDb = LocalDate.parse(lastDateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        	                
        	                // 로그 추가: 변환된 날짜 출력
        	                System.out.println("🔍 변환된 날짜: " + lastDateInDb);
        	                
        	            } catch (DateTimeParseException e) {
        	                // 변환 실패 시 로그를 출력하고 예외를 던지거나 적절히 처리
        	                System.err.println("❌ 날짜 형식 변환 오류: " + lastDateStr);
        	                throw e;  // 예외를 다시 던지거나 적절히 처리
        	            }
        	        }
        	    }
        	}



            // 1번 DB에서 모든 데이터를 집계할 때, 기준일을 설정할 필요 없음
            if (lastDateInDb == null) {
                System.out.println("✅ DB에 데이터가 없습니다. 전체 데이터를 집계합니다.");
                // 전체 데이터를 집계하려면 날짜 범위를 설정할 필요 없이 데이터를 모두 가져옴
                lastDateInDb = LocalDate.now();  // 기준일을 현재 날짜로 설정하여 전체 데이터 조회
            } else {
                // 출력 시 하이픈 없이 `yyyyMMdd` 형식으로 출력
                String formattedLastDate = lastDateInDb.format(DateTimeFormatter.BASIC_ISO_DATE);
                System.out.println("기존 DB 마지막 날짜: " + formattedLastDate);

                LocalDate currentDate = LocalDate.now();
                LocalDate twelveMonthsAgo = lastDateInDb.minusMonths(12);  // 기준일로부터 12개월 전 계산
                System.out.println("집계 기간: " + twelveMonthsAgo.format(DateTimeFormatter.BASIC_ISO_DATE) + " ~ " + currentDate.format(DateTimeFormatter.BASIC_ISO_DATE));
             // 12개월 전부터 현재까지 데이터를 집계
                String query = "SELECT MATCH_SEQ_NO, MATCH_DE, GRP_NM, STDM_NM, SPORTS_VIEWNG_NMPR_CO, COLCT_DE, UPDT_DE " +
                               "FROM colct_sports_match_info WHERE MATCH_DE BETWEEN ? AND ?";

                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    // 날짜를 yyyyMMdd 형식으로 변환하여 문자열로 전달
                    String twelveMonthsAgoStr = twelveMonthsAgo.format(DateTimeFormatter.BASIC_ISO_DATE); // 12개월 전 날짜
                    String currentDateStr = currentDate.format(DateTimeFormatter.BASIC_ISO_DATE); // 현재 날짜

                    stmt.setString(1, twelveMonthsAgoStr);  // 12개월 전 날짜
                    stmt.setString(2, currentDateStr);      // 현재 날짜

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String matchSeq = rs.getString("MATCH_SEQ_NO");
                            if (!uniqueMatchSeqSet.add(matchSeq)) continue;

                            String matchDe = rs.getString("MATCH_DE"); // MATCH_DE 값 읽기
                            String grpNm = rs.getString("GRP_NM");
                            String stdmNm = rs.getString("STDM_NM");
                            double viewCnt = rs.getDouble("SPORTS_VIEWNG_NMPR_CO");
                            String colctDe = rs.getString("COLCT_DE");
                            String updtDe = rs.getString("UPDT_DE");

                            // 구장명 매핑 체크
                            if (!stdmToCityName.containsKey(stdmNm)) {
                                System.err.println("❌ 매핑되지 않은 구장명 발견: " + stdmNm);
                                throw new RuntimeException("구장 매핑 실패: " + stdmNm);
                            }

                            String cityName = stdmToCityName.get(stdmNm);
                            String cityCode = cityNameToCode.getOrDefault(cityName, "99");
                            String key = matchDe + cityCode;

                            Stat stat = map.getOrDefault(key, new Stat());
                            stat.baseDe = matchDe;  // matchDe를 BASE_DE로 저장
                            stat.cityName = cityName;
                            stat.cityCode = cityCode;
                            stat.colctDe = colctDe;
                            stat.updtDe = updtDe;

                            // 집계 그룹별로 데이터를 추가
                            switch (grpNm.toUpperCase()) {
                                case "KBO" -> { stat.kbo += viewCnt; stat.kboCount++; }
                                case "KBL" -> { stat.kbl += viewCnt; stat.kblCount++; }
                                case "WKBL" -> { stat.wkbl += viewCnt; stat.wkblCount++; }
                                case "KOVO" -> { stat.kovo += viewCnt; stat.kovoCount++; }
                                case "KLEAGUE" -> { stat.klea += viewCnt; stat.kleaCount++; }
                            }

                            map.put(key, stat);
                        }
                    }
                }



                // 4. 데이터 중복 검사 및 업데이트/삽입
                String checkDuplicateSql = "SELECT COUNT(*) FROM colct_sports_viewng_info_stat WHERE BASE_DE = ? AND CTPRVN_CD = ? AND MATCH_DE BETWEEN ? AND ?";

                try (PreparedStatement checkStmt = conn.prepareStatement(checkDuplicateSql)) {
                    int insertCount = 0;
                    int updateCount = 0;

                    for (Stat s : map.values()) {
                        String baseDe = s.baseDe.trim();  // 공백 제거
                        String cityCode = s.cityCode.trim();  // 공백 제거

                        // 로그: 파라미터 설정 확인
                        System.out.println("🔍 업데이트 조건: BASE_DE = " + baseDe + ", CTPRVN_CD = " + cityCode);

                        checkStmt.setString(1, baseDe);   
                        checkStmt.setString(2, cityCode);
                        checkStmt.setString(3, twelveMonthsAgo.toString());  // 12개월 전 날짜
                        checkStmt.setString(4, currentDate.toString());      // 현재 날짜

                        try (ResultSet rs = checkStmt.executeQuery()) {
                            if (rs.next() && rs.getInt(1) > 0) {
                                System.out.println("✅ 데이터가 존재합니다. 업데이트 실행.");
                                updateData(conn, s);  // 데이터 업데이트
                                updateCount++;
                            } else {
                                System.out.println("❌ 데이터가 없어서 삽입 작업을 실행합니다.");
                                insertData(conn, s);  // 데이터 삽입
                                insertCount++;
                            }
                        }
                    }

                    // 최종 로그 출력
                    System.out.println("✅ 총 " + insertCount + "개의 새로운 데이터가 삽입되었습니다.");
                    System.out.println("✅ 총 " + updateCount + "개의 데이터가 업데이트되었습니다.");
                }

            }
        }
    }


    private static void updateData(Connection conn, Stat s) throws SQLException {
        String updateSql = "UPDATE colct_sports_viewng_info_stat SET " +
                "BASE_DE = ?, BASE_YEAR = ?, BASE_MT = ?, BASE_DAY = ?, CTPRVN_CD = ?, CTPRVN_NM = ?, " +
                "KLEA_VIEWNG_NMPR_CO = ?, KBO_VIEWNG_NMPR_CO = ?, KBL_VIEWNG_NMPR_CO = ?, WKBL_VIEWNG_NMPR_CO = ?, " +
                "KOVO_VIEWNG_NMPR_CO = ?, SPORTS_VIEWNG_NMPR_CO = ?, KLEA_MATCH_CO = ?, KBO_MATCH_CO = ?, " +
                "KBL_MATCH_CO = ?, WKBL_MATCH_CO = ?, KOVO_MATCH_CO = ?, SPORTS_MATCH_CO = ?, " +
                "COLCT_DE = ?, UPDT_DE = ? " +
                "WHERE BASE_DE = ? AND CTPRVN_CD = ?"; 

        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
            // 파라미터 설정 (순서대로 맞추기)
            updateStmt.setString(1, s.baseDe);          // BASE_DE
            updateStmt.setString(2, s.baseDe.substring(0, 4)); // BASE_YEAR
            updateStmt.setString(3, s.baseDe.substring(4, 6)); // BASE_MT
            updateStmt.setString(4, s.baseDe.substring(6, 8)); // BASE_DAY
            updateStmt.setString(5, s.cityCode);        // CTPRVN_CD
            updateStmt.setString(6, s.cityName);        // CTPRVN_NM
            updateStmt.setDouble(7, s.klea);            // KLEA_VIEWNG_NMPR_CO
            updateStmt.setDouble(8, s.kbo);             // KBO_VIEWNG_NMPR_CO
            updateStmt.setDouble(9, s.kbl);             // KBL_VIEWNG_NMPR_CO
            updateStmt.setDouble(10, s.wkbl);           // WKBL_VIEWNG_NMPR_CO
            updateStmt.setDouble(11, s.kovo);           // KOVO_VIEWNG_NMPR_CO
            updateStmt.setDouble(12, s.klea + s.kbo + s.kbl + s.wkbl + s.kovo);  // SPORTS_VIEWNG_NMPR_CO
            updateStmt.setInt(13, s.kleaCount);         // KLEA_MATCH_CO
            updateStmt.setInt(14, s.kboCount);          // KBO_MATCH_CO
            updateStmt.setInt(15, s.kblCount);          // KBL_MATCH_CO
            updateStmt.setInt(16, s.wkblCount);        // WKBL_MATCH_CO
            updateStmt.setInt(17, s.kovoCount);        // KOVO_MATCH_CO
            updateStmt.setInt(18, s.kleaCount + s.kboCount + s.kblCount + s.wkblCount + s.kovoCount); // SPORTS_MATCH_CO
            updateStmt.setString(19, s.colctDe);       // COLCT_DE
            updateStmt.setString(20, s.updtDe);        // UPDT_DE
            updateStmt.setString(21, s.baseDe);        // WHERE BASE_DE
            updateStmt.setString(22, s.cityCode);      // WHERE CTPRVN_CD

            // SQL 실행
            updateStmt.executeUpdate();
        }
    }

    private static void insertData(Connection conn, Stat s) throws SQLException {
        String insertSql = "INSERT INTO colct_sports_viewng_info_stat (BASE_DE, BASE_YEAR, BASE_MT, BASE_DAY, CTPRVN_CD, CTPRVN_NM, " +
                "KLEA_VIEWNG_NMPR_CO, KBO_VIEWNG_NMPR_CO, KBL_VIEWNG_NMPR_CO, WKBL_VIEWNG_NMPR_CO, KOVO_VIEWNG_NMPR_CO, " +
                "SPORTS_VIEWNG_NMPR_CO, KLEA_MATCH_CO, KBO_MATCH_CO, KBL_MATCH_CO, WKBL_MATCH_CO, KOVO_MATCH_CO, SPORTS_MATCH_CO, " +
                "COLCT_DE, UPDT_DE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            insertStmt.setString(1, s.baseDe);          // BASE_DE (MATCH_DE을 BASE_DE로 삽입)
            insertStmt.setString(2, s.baseDe.substring(0, 4)); // BASE_YEAR
            insertStmt.setString(3, s.baseDe.substring(4, 6)); // BASE_MT
            insertStmt.setString(4, s.baseDe.substring(6, 8)); // BASE_DAY
            insertStmt.setString(5, s.cityCode);        // CTPRVN_CD
            insertStmt.setString(6, s.cityName);        // CTPRVN_NM
            insertStmt.setDouble(7, s.klea);            // KLEA_VIEWNG_NMPR_CO
            insertStmt.setDouble(8, s.kbo);             // KBO_VIEWNG_NMPR_CO
            insertStmt.setDouble(9, s.kbl);             // KBL_VIEWNG_NMPR_CO
            insertStmt.setDouble(10, s.wkbl);           // WKBL_VIEWNG_NMPR_CO
            insertStmt.setDouble(11, s.kovo);           // KOVO_VIEWNG_NMPR_CO
            insertStmt.setDouble(12, s.klea + s.kbo + s.kbl + s.wkbl + s.kovo);  // SPORTS_VIEWNG_NMPR_CO
            insertStmt.setInt(13, s.kleaCount);         // KLEA_MATCH_CO
            insertStmt.setInt(14, s.kboCount);          // KBO_MATCH_CO
            insertStmt.setInt(15, s.kblCount);          // KBL_MATCH_CO
            insertStmt.setInt(16, s.wkblCount);        // WKBL_MATCH_CO
            insertStmt.setInt(17, s.kovoCount);        // KOVO_MATCH_CO
            insertStmt.setInt(18, s.kleaCount + s.kboCount + s.kblCount + s.wkblCount + s.kovoCount); // SPORTS_MATCH_CO
            insertStmt.setString(19, s.colctDe);       // COLCT_DE
            insertStmt.setString(20, s.updtDe);        // UPDT_DE

            // SQL 실행
            insertStmt.executeUpdate();
        }
    }


}