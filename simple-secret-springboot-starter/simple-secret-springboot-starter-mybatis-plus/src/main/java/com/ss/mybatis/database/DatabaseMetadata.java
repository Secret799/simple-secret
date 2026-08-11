package com.ss.mybatis.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** 使用调用方显式提供的数据源读取 JDBC 元数据。 */
public final class DatabaseMetadata {

    private DatabaseMetadata() {
    }

    /**
     * 识别数据源当前连接的数据库类型。
     *
     * @param dataSource 数据源
     * @return 数据库类型
     * @throws DatabaseMetadataException 获取连接或元数据失败时抛出
     */
    public static DatabaseType detect(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return DatabaseType.fromProductName(productName);
        } catch (SQLException exception) {
            throw new DatabaseMetadataException(
                    "Failed to inspect database metadata", exception);
        }
    }
}
