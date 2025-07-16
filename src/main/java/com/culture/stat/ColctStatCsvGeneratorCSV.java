package com.culture.stat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ColctStatCsvGeneratorCSV {

    static class Stat {
        String year, month, cityName, cityCode;
        double klea = 0, kbo = 0, kbl = 0, wkbl = 0, kovo = 0;
        int kleaCount = 0, kboCount = 0, kblCount = 0, wkblCount = 0, kovoCount = 0;
        String colctDe = "", updtDe = "";
        String baseDay;

        public String toCsvLine() {
            // 따옴표 및 공백 제거
            String cleanMonth = month.replaceAll("\"", "").trim();
            String cleanDay = baseDay.replaceAll("\"", "").trim();

            // 안전하게 baseDe 생성
            String baseDe = year + String.format("%02d", Integer.parseInt(cleanMonth)) + 
                            String.format("%02d", Integer.parseInt(cleanDay));

            double totalView = klea + kbo + kbl + wkbl + kovo;
            int totalCnt = kleaCount + kboCount + kblCount + wkblCount + kovoCount;

            return String.join(",", baseDe, year, cleanMonth, cleanDay, cityCode, cityName,
                    format(klea), format(kbo), format(kbl), format(wkbl), format(kovo), format(totalView),
                    String.valueOf(kleaCount), String.valueOf(kboCount), String.valueOf(kblCount),
                    String.valueOf(wkblCount), String.valueOf(kovoCount), String.valueOf(totalCnt),
                    colctDe, updtDe);
        }


        private String format(double v) {
            return String.format("%.5f", v);
        }
    }

    static final Map<String, String> stdmToCityName = Map.ofEntries(
        Map.entry("잠실실내체육관", "서울"), Map.entry("고척", "서울"), Map.entry("잠실", "서울"), Map.entry("목동 종합", "서울"),
        Map.entry("창원체육관", "경남"), Map.entry("창원 축구센터", "경남"),
        Map.entry("울산동천체육관", "울산"), Map.entry("울산 문수", "울산"), Map.entry("울산 종합", "울산"),
        Map.entry("고양 소노 아레나", "경기"), Map.entry("고양체육관", "경기"),
        Map.entry("수원 KT 아레나", "경기"), Map.entry("수원 종합", "경기"), Map.entry("수원 월드컵", "경기"), Map.entry("수원", "경기"),
        Map.entry("안양 정관장 아레나", "경기"), Map.entry("안양 종합", "경기"),
        Map.entry("광주 월드컵", "광주"), Map.entry("광주 전용", "광주"),
        Map.entry("인천 전용", "인천"), Map.entry("대전 월드컵", "대전"), Map.entry("대구", "대구"),
        Map.entry("포항", "경북"), Map.entry("전주 월드컵", "전북"), Map.entry("제주 월드컵", "제주"),
        Map.entry("잠실학생체육관", "서울"),
        Map.entry("부산사직체육관", "부산"),
        Map.entry("대구체육관", "대구"),
        Map.entry("원주종합체육관", "강원"),
        Map.entry("이천 LG 챔피언스파크", "경기"),
        Map.entry("제천체육관", "충북"),
        Map.entry("군산월명체육관", "전북"),
        Map.entry("안양실내체육관", "경기"),
        Map.entry("전주실내체육관", "전북"),
        Map.entry("문학", "인천"),
        Map.entry("대전(신)", "대전"),
        Map.entry("사직", "부산"),
        Map.entry("부산 아시아드", "부산"),
        Map.entry("대구iM뱅크PARK", "대구"),
        Map.entry("강릉하이원아레나", "강원"),
        Map.entry("탄천 종합", "경기"),
        Map.entry("청주 종합", "충북"),
        Map.entry("춘천 송암", "강원"),
        Map.entry("부천 종합", "경기"),
        Map.entry("광양 전용", "전남"),
        Map.entry("아산 이순신", "충남"),
        Map.entry("안산 와스타디움", "경기"),
        Map.entry("김포솔터축구장", "경기"),
        Map.entry("양산 종합", "경남"),
        Map.entry("부산 구덕", "부산"),
        Map.entry("천안 종합", "충남"),
        Map.entry("김천 종합", "경북"),
        Map.entry("용인 미르", "경기"),
        Map.entry("화성 종합", "경기"),
        Map.entry("광주", "광주"),
        Map.entry("여수", "전남"),  // 예상 매핑
        Map.entry("포항 스틸야드", "경북")

    );

    static final Map<String, String> cityNameToCode = Map.ofEntries(
        Map.entry("서울", "11"), Map.entry("부산", "26"), Map.entry("대구", "27"), Map.entry("인천", "28"),
        Map.entry("광주", "29"), Map.entry("대전", "30"), Map.entry("울산", "31"), Map.entry("세종", "36"),
        Map.entry("경기", "41"), Map.entry("강원", "42"), Map.entry("충북", "43"), Map.entry("충남", "44"),
        Map.entry("전북", "45"), Map.entry("전남", "46"), Map.entry("경북", "47"), Map.entry("경남", "48"), Map.entry("제주", "50")
    );

    public static void main(String[] args) throws Exception {
        String inputPath = "C:\\eGovFrameDev-4.2.0-64bit\\workspace\\culture-observatory-crawler\\colct_sports_match_info.csv";
        String outputPath = "C:\\eGovFrameDev-4.2.0-64bit\\workspace\\culture-observatory-crawler\\colct_sports_viewng_info_stat.csv";

        Map<String, Stat> map = new LinkedHashMap<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8))) {
            String header = br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] t = line.split(",", -1);
                if (t.length < 13) continue;

                String year = t[2];
                String month = t[3];
                String baseDay = t[4]; 
                String grpNm = t[5];
                String stdmNm = t[9];
                double viewCnt = Double.parseDouble(t[10]);
                String colctDe = t[11];
                String updtDe = t[12];

                String cityName = stdmToCityName.getOrDefault(stdmNm, "기타");
                String cityCode = cityNameToCode.getOrDefault(cityName, "99");
                String key = year + month + baseDay + cityCode; 

                Stat stat = map.getOrDefault(key, new Stat());
                stat.baseDay = baseDay;
                stat.year = year;
                stat.month = month;
                stat.cityName = cityName;
                stat.cityCode = cityCode;
                stat.colctDe = colctDe;
                stat.updtDe = updtDe;

                switch (grpNm.toUpperCase()) {
                    case "KBO":
                        stat.kbo += viewCnt;
                        stat.kboCount++;
                        break;
                    case "KBL":
                        stat.kbl += viewCnt;
                        stat.kblCount++;
                        break;
                    case "WKBL":
                        stat.wkbl += viewCnt;
                        stat.wkblCount++;
                        break;
                    case "KOVO":
                        stat.kovo += viewCnt;
                        stat.kovoCount++;
                        break;
                    case "KLEAGUE":
                        stat.klea += viewCnt;
                        stat.kleaCount++;
                        break;

                }
                map.put(key, stat);
            }
        }

        // write CSV
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8))) {
            pw.println("BASE_DE,BASE_YEAR,BASE_MT,BASE_DAY,CTPRVN_CD,CTPRVN_NM," +
                    "KLEA_VIEWNG_NMPR_CO,KBO_VIEWNG_NMPR_CO,KBL_VIEWNG_NMPR_CO,WKBL_VIEWNG_NMPR_CO,KOVO_VIEWNG_NMPR_CO,SPORTS_VIEWNG_NMPR_CO," +
                    "KLEA_MATCH_CO,KBO_MATCH_CO,KBL_MATCH_CO,WKBL_MATCH_CO,KOVO_MATCH_CO,SPORTS_MATCH_CO,COLCT_DE,UPDT_DE");

            for (Stat s : map.values()) {
                pw.println(s.toCsvLine());
            }
        }

        System.out.println("✅ 집계 완료: " + outputPath);
    }
}
