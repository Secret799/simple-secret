package com.ss.mybatis.database;

import java.util.Locale;

/** 常见关系数据库产品类型。 */
public enum DatabaseType {
    /** MySQL 或 MariaDB。 */
    MYSQL,
    /** PostgreSQL。 */
    POSTGRESQL,
    /** Oracle Database。 */
    ORACLE,
    /** Microsoft SQL Server。 */
    SQL_SERVER,
    /** H2 Database。 */
    H2,
    /** 未识别的数据库。 */
    UNKNOWN;

    /**
     * 根据 JDBC 产品名识别数据库类型。
     *
     * @param productName JDBC 数据库产品名
     * @return 数据库类型
     */
    public static DatabaseType fromProductName(String productName) {
        if (productName == null || productName.isBlank()) {
            return UNKNOWN;
        }
        String normalized = productName.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("mysql") || normalized.contains("mariadb")) {
            return MYSQL;
        }
        if (normalized.contains("postgresql")) {
            return POSTGRESQL;
        }
        if (normalized.contains("oracle")) {
            return ORACLE;
        }
        if (normalized.contains("microsoft sql server")) {
            return SQL_SERVER;
        }
        if (normalized.equals("h2")) {
            return H2;
        }
        return UNKNOWN;
    }
}
