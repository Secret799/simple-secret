package com.ss.mybatis.database;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/** 验证数据库类型识别使用显式数据源并正确关闭连接。 */
class DatabaseMetadataTest {

    @Test
    void shouldDetectKnownAndUnknownDatabaseProducts() {
        assertThat(DatabaseType.fromProductName("MySQL")).isEqualTo(DatabaseType.MYSQL);
        assertThat(DatabaseType.fromProductName("PostgreSQL")).isEqualTo(DatabaseType.POSTGRESQL);
        assertThat(DatabaseType.fromProductName("Oracle")).isEqualTo(DatabaseType.ORACLE);
        assertThat(DatabaseType.fromProductName("Microsoft SQL Server"))
                .isEqualTo(DatabaseType.SQL_SERVER);
        assertThat(DatabaseType.fromProductName("H2")).isEqualTo(DatabaseType.H2);
        assertThat(DatabaseType.fromProductName("custom-db")).isEqualTo(DatabaseType.UNKNOWN);
        assertThat(DatabaseType.fromProductName(null)).isEqualTo(DatabaseType.UNKNOWN);
    }

    @Test
    void shouldDetectFromDataSourceAndCloseConnection() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        java.sql.DatabaseMetaData metadata = proxy(java.sql.DatabaseMetaData.class,
                (proxy, method, args) -> "getDatabaseProductName".equals(method.getName())
                        ? "PostgreSQL" : defaultValue(method.getReturnType()));
        Connection connection = proxy(Connection.class, (proxy, method, args) -> {
            if ("getMetaData".equals(method.getName())) {
                return metadata;
            }
            if ("close".equals(method.getName())) {
                closed.set(true);
                return null;
            }
            return defaultValue(method.getReturnType());
        });
        DataSource dataSource = proxy(DataSource.class,
                (proxy, method, args) -> "getConnection".equals(method.getName())
                        ? connection : defaultValue(method.getReturnType()));

        assertThat(DatabaseMetadata.detect(dataSource)).isEqualTo(DatabaseType.POSTGRESQL);

        assertThat(closed).isTrue();
    }

    @Test
    void shouldPreserveSqlExceptionAsCause() throws Exception {
        SQLException cause = new SQLException("credential detail");
        DataSource dataSource = proxy(DataSource.class, (proxy, method, args) -> {
            if ("getConnection".equals(method.getName())) {
                throw cause;
            }
            return defaultValue(method.getReturnType());
        });

        assertThatThrownBy(() -> DatabaseMetadata.detect(dataSource))
                .isInstanceOf(DatabaseMetadataException.class)
                .hasMessage("Failed to inspect database metadata")
                .hasCause(cause);
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
