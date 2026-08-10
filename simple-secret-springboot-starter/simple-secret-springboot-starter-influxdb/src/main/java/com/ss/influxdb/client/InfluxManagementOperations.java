package com.ss.influxdb.client;

import com.ss.influxdb.exception.InfluxOperationException;
import com.ss.influxdb.query.InfluxIdentifiers;
import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

import java.util.List;
import java.util.Objects;

/**
 * 使用无默认数据库上下文的 InfluxQL 执行数据库管理命令。
 *
 * @author junpzx
 * @since 2026-08-10
 */
public class InfluxManagementOperations implements InfluxManagement {
    /** InfluxDB 客户端。 */
    private final InfluxDB client;

    /**
     * 创建管理操作入口。
     *
     * @param client InfluxDB 客户端
     */
    public InfluxManagementOperations(InfluxDB client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    /** {@inheritDoc} */
    @Override
    public boolean databaseExists(String database) {
        String name = InfluxIdentifiers.identifier(database);
        QueryResult result = query("SHOW DATABASES");
        if (result.getResults() == null) {
            return false;
        }
        return result.getResults().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getSeries() != null)
                .flatMap(item -> item.getSeries().stream())
                .anyMatch(series -> containsFirstColumn(series, name));
    }

    /** {@inheritDoc} */
    @Override
    public void createDatabase(String database) {
        String name = InfluxIdentifiers.identifier(database);
        query("CREATE DATABASE " + InfluxIdentifiers.quote(name));
    }

    /** {@inheritDoc} */
    @Override
    public boolean retentionPolicyExists(String database, String retentionPolicy) {
        String databaseName = InfluxIdentifiers.identifier(database);
        String policyName = InfluxIdentifiers.identifier(retentionPolicy);
        QueryResult result = query(
                "SHOW RETENTION POLICIES ON " + InfluxIdentifiers.quote(databaseName));
        if (result.getResults() == null) {
            return false;
        }
        for (QueryResult.Result item : result.getResults()) {
            if (item == null || item.getSeries() == null) {
                continue;
            }
            for (QueryResult.Series series : item.getSeries()) {
                if (containsFirstColumn(series, policyName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void createRetentionPolicy(String database, String retentionPolicy, String duration,
                                      int replication, boolean defaultPolicy) {
        String databaseName = InfluxIdentifiers.identifier(database);
        String policyName = InfluxIdentifiers.identifier(retentionPolicy);
        String validatedDuration = InfluxIdentifiers.duration(duration);
        if (replication <= 0) {
            throw new IllegalArgumentException("InfluxDB retention policy replication must be positive");
        }
        String command = "CREATE RETENTION POLICY " + InfluxIdentifiers.quote(policyName)
                + " ON " + InfluxIdentifiers.quote(databaseName)
                + " DURATION " + validatedDuration
                + " REPLICATION " + replication
                + (defaultPolicy ? " DEFAULT" : "");
        query(command);
    }

    private QueryResult query(String command) {
        try {
            QueryResult result = client.query(new Query(command));
            validateQueryResult(result);
            return result;
        } catch (InfluxOperationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InfluxOperationException(
                    "Unable to execute InfluxDB management query", safeClientCause(exception));
        }
    }

    private static void validateQueryResult(QueryResult result) {
        if (result == null) {
            throw new InfluxOperationException("InfluxDB management query returned no result object");
        }
        if (result.hasError()) {
            throw new InfluxOperationException("InfluxDB management query returned a server error");
        }
        if (result.getResults() == null) {
            return;
        }
        for (QueryResult.Result item : result.getResults()) {
            if (item != null && item.hasError()) {
                throw new InfluxOperationException(
                        "InfluxDB management query result item returned a server error");
            }
        }
    }

    private static boolean containsFirstColumn(QueryResult.Series series, String expected) {
        if (series == null || series.getValues() == null) {
            return false;
        }
        for (List<Object> row : series.getValues()) {
            if (row != null && !row.isEmpty() && expected.equals(String.valueOf(row.get(0)))) {
                return true;
            }
        }
        return false;
    }

    private static RuntimeException safeClientCause(RuntimeException exception) {
        return new IllegalStateException(
                "InfluxDB client failure type: " + exception.getClass().getName());
    }
}
