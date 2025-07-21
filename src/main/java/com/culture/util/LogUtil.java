package com.culture.util;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class LogUtil {


    // 로그 삽입 함수
    public static void insertLog(
            String groupNm, String jobNm, String tableNm, String stateCd,
            Integer collectedCount, Integer insertedCount, Integer updatedCount, String errorMsg, String today
    ) {
        // 로그 출력: 삽입하기 전의 값 확인
        System.out.println("📌 로그 삽입 직전 - 그룹명: " + groupNm);
        System.out.println("📌 작업명: " + jobNm);
        System.out.println("📌 테이블명: " + tableNm);
        System.out.println("📌 상태 코드: " + stateCd);
        System.out.println("📌 수집된 데이터 개수 (collectedCount): " + collectedCount);
        System.out.println("📌 삽입된 데이터 개수 (insertedCount): " + insertedCount);
        System.out.println("📌 업데이트된 데이터 개수 (updatedCount): " + updatedCount);
        System.out.println("📌 에러 메시지 (errorMsg): " + errorMsg);

        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul",
                "root", "1234"
        )) {
            // 로그 테이블에 데이터 삽입
            String insertLogSql = "INSERT INTO colct_schd_log (" +
                    "COLCT_SCHD_GROUP_NM, COLCT_SCHD_JOB_NM, COLCT_SCHD_TABLE_NM, " +
                    "COLCT_STATE_CD, COLCT_CO, CRTN_CO, UPDT_CO, ERROR_MSG, COLCT_DT) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

            PreparedStatement pstmtLog = conn.prepareStatement(insertLogSql);
            pstmtLog.setString(1, groupNm);
            pstmtLog.setString(2, jobNm);
            pstmtLog.setString(3, tableNm);
            pstmtLog.setString(4, stateCd);

            // null-safe 처리
            if (collectedCount != null) {
                pstmtLog.setInt(5, collectedCount);
            } else {
                pstmtLog.setNull(5, java.sql.Types.INTEGER);
            }

            if (insertedCount != null) {
                pstmtLog.setInt(6, insertedCount);
            } else {
                pstmtLog.setNull(6, java.sql.Types.INTEGER);
            }

            if (updatedCount != null) {
                pstmtLog.setInt(7, updatedCount);
            } else {
                pstmtLog.setNull(7, java.sql.Types.INTEGER);
            }

            pstmtLog.setString(8, errorMsg);


            int result = pstmtLog.executeUpdate();
            if (result > 0) {
                System.out.println("✅ 로그 삽입 성공");
            } else {
                System.err.println("❌ 로그 삽입 실패: 테이블에 데이터가 삽입되지 않음.");
            }
            pstmtLog.close();
        } catch (SQLException e) {
            System.err.println("❌ 로그 삽입 실패: " + e.getMessage());
            System.err.println("    SQLState: " + e.getSQLState());
            System.err.println("    ErrorCode: " + e.getErrorCode());
        }
    }
    
 // 수집 플래그 삽입 함수
    public static void insertFlag(String date, String tableName, boolean flag) {
        System.out.println("📌 플래그 삽입 - 날짜: " + date + ", 테이블명: " + tableName + ", 플래그: " + flag);

        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/culture_crawler_db?serverTimezone=Asia/Seoul",
                "root", "1234"
        )) {
            // INSERT IGNORE 또는 ON DUPLICATE KEY UPDATE도 가능
            String insertFlagSql = "INSERT INTO colct_daly_flag (" +
                    "colct_daly_de, colct_daly_table_nm, colct_daly_flag, colct_dt) " +
                    "VALUES (?, ?, ?, CURRENT_TIMESTAMP)";

            try (PreparedStatement pstmtFlag = conn.prepareStatement(insertFlagSql)) {
                pstmtFlag.setString(1, date);
                pstmtFlag.setString(2, tableName);
                pstmtFlag.setBoolean(3, flag);

                int result = pstmtFlag.executeUpdate();
                if (result > 0) {
                    System.out.println("✅ 플래그 삽입 성공");
                } else {
                    System.err.println("❌ 플래그 삽입 실패: 중복이거나 무시됨");
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ 플래그 삽입 예외 발생: " + e.getMessage());
            System.err.println("    SQLState: " + e.getSQLState());
            System.err.println("    ErrorCode: " + e.getErrorCode());
        }
    }

    
    
}
