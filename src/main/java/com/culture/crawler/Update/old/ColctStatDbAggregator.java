package com.culture.crawler.Update.old;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ColctStatDbAggregator {

	static class Stat {
	    String baseDe, cityName, cityCode;
	    double klea = 0, kbo = 0, kbl = 0, wkbl = 0, kovo = 0;
	    int kleaCount = 0, kboCount = 0, kblCount = 0, wkblCount = 0, kovoCount = 0;
	    String colctDe = "", updtDe = "";

	    public void insertToDb(PreparedStatement pstmt) throws SQLException {
	        double totalView = klea + kbo + kbl + wkbl + kovo;
	        int totalCnt = kleaCount + kboCount + kblCount + wkblCount + kovoCount;

	        pstmt.setString(1, baseDe);
	        pstmt.setString(2, baseDe.substring(0, 4)); // BASE_YEAR
	        pstmt.setString(3, baseDe.substring(4, 6)); // BASE_MT
	        pstmt.setString(4, baseDe.substring(6, 8)); // BASE_DAY
	        pstmt.setString(5, cityCode);
	        pstmt.setString(6, cityName);
	        pstmt.setDouble(7, klea);
	        pstmt.setDouble(8, kbo);
	        pstmt.setDouble(9, kbl);
	        pstmt.setDouble(10, wkbl);
	        pstmt.setDouble(11, kovo);
	        pstmt.setDouble(12, totalView);
	        pstmt.setInt(13, kleaCount);
	        pstmt.setInt(14, kboCount);
	        pstmt.setInt(15, kblCount);
	        pstmt.setInt(16, wkblCount);
	        pstmt.setInt(17, kovoCount);
	        pstmt.setInt(18, totalCnt);
	        pstmt.setString(19, colctDe);
	        pstmt.setString(20, updtDe);
	        pstmt.addBatch();
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
        	String query = "SELECT MATCH_SEQ_NO, MATCH_DE, GRP_NM, STDM_NM, SPORTS_VIEWNG_NMPR_CO, COLCT_DE, UPDT_DE FROM colct_sports_match_info";
        	try (PreparedStatement stmt = conn.prepareStatement(query);
        	     ResultSet rs = stmt.executeQuery()) {

        	    while (rs.next()) {
        	    	String matchSeq = rs.getString("MATCH_SEQ_NO");
        	    	if (!uniqueMatchSeqSet.add(matchSeq)) continue; // 이미 처리된 경기면 skip
        	        String baseDe = rs.getString("MATCH_DE");
        	        String grpNm = rs.getString("GRP_NM");
        	        String stdmNm = rs.getString("STDM_NM");
        	        double viewCnt = rs.getDouble("SPORTS_VIEWNG_NMPR_CO");
        	        String colctDe = rs.getString("COLCT_DE");
        	        String updtDe = rs.getString("UPDT_DE");

        	        if (!stdmToCityName.containsKey(stdmNm)) {
        	            System.err.println("❌ 매핑되지 않은 구장명 발견: " + stdmNm);
        	            throw new RuntimeException("구장 매핑 실패: " + stdmNm);
        	        }
        	        String cityName = stdmToCityName.get(stdmNm);
        	        String cityCode = cityNameToCode.getOrDefault(cityName, "99");
        	        String key = baseDe + cityCode;

        	        Stat stat = map.getOrDefault(key, new Stat());
        	        stat.baseDe = baseDe;
        	        stat.cityName = cityName;
        	        stat.cityCode = cityCode;
        	        stat.colctDe = colctDe;
        	        stat.updtDe = updtDe;

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


            // INSERT 집계 결과
            String insertSql = "INSERT INTO colct_sports_viewng_info_stat (BASE_DE, BASE_YEAR, BASE_MT, BASE_DAY, CTPRVN_CD, CTPRVN_NM, " +
                    "KLEA_VIEWNG_NMPR_CO, KBO_VIEWNG_NMPR_CO, KBL_VIEWNG_NMPR_CO, WKBL_VIEWNG_NMPR_CO, KOVO_VIEWNG_NMPR_CO, SPORTS_VIEWNG_NMPR_CO, " +
                    "KLEA_MATCH_CO, KBO_MATCH_CO, KBL_MATCH_CO, WKBL_MATCH_CO, KOVO_MATCH_CO, SPORTS_MATCH_CO, COLCT_DE, UPDT_DE) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                for (Stat s : map.values()) {
                    s.insertToDb(pstmt);
                }
                pstmt.executeBatch();
                System.out.println("✅ DB 집계 및 저장 완료");
            }
        }
    }
}
