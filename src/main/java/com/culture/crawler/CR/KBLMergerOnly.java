package com.culture.crawler.CR;

import java.io.*;
import java.util.*;

public class KBLMergerOnly {
    public static void main(String[] args) throws Exception {
        String baseDir = System.getProperty("user.dir");

        String scheduleCsv = baseDir + File.separator + "kbl_matchinfo_2024_from_schedule.csv";
        String crowdCsv = baseDir + File.separator + "kbl_matchinfo_2024.csv";
        String outputCsv = baseDir + File.separator + "kbl_matchinfo_merged.csv";

        mergeCrowdAndScheduleCsv(scheduleCsv, crowdCsv, outputCsv);
    }

    public static void mergeCrowdAndScheduleCsv(String scheduleCsvPath, String crowdCsvPath, String outputCsvPath) throws IOException {
        List<String[]> scheduleRows = new ArrayList<>();
        List<String[]> crowdRows = new ArrayList<>();

        try (BufferedReader scheduleReader = new BufferedReader(new FileReader(scheduleCsvPath));
             BufferedReader crowdReader = new BufferedReader(new FileReader(crowdCsvPath))) {

            scheduleReader.readLine(); // skip header
            crowdReader.readLine();

            String line;
            while ((line = scheduleReader.readLine()) != null) {
                scheduleRows.add(line.split(",", -1));
            }
            while ((line = crowdReader.readLine()) != null) {
                crowdRows.add(line.split(",", -1));
            }
        }

        List<String> mergedLines = new ArrayList<>();
        mergedLines.add("MATCH_DE,BASE_YEAR,BASE_MT,BASE_DAY,GRP_NM,LEA_NM,HOME_TEAM_NM,AWAY_TEAM_NM,STDM_NM,SPORTS_VIEWNG_NMPR_CO,COLCT_DE,UPDT_DE");

        for (String[] schedule : scheduleRows) {
            String matchDe = schedule[0].trim();
            String homeTeam = schedule[6].replaceAll("\\s+", "").toLowerCase();

            String matchedCrowd = null;

            for (String[] crowd : crowdRows) {
                if (!crowd[0].trim().equals(matchDe)) continue;

                String crowdHome = crowd[6].replaceAll("\\s+", "").toLowerCase();

                if (homeTeam.equals(crowdHome) ||
                    homeTeam.contains(crowdHome) ||
                    crowdHome.contains(homeTeam)) {
                    matchedCrowd = crowd[9];
                    break;
                }
            }

            if (matchedCrowd != null && !matchedCrowd.isEmpty()) {
                schedule[9] = matchedCrowd;
            }

            mergedLines.add(String.join(",", schedule));
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputCsvPath))) {
            for (String l : mergedLines) {
                writer.println(l);
            }
        }

        System.out.println("✅ 병합 완료 → " + outputCsvPath);
    }
}
