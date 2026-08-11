package com.ss.mybatis.page;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 第三方调用方可直接使用的安全分页查询参数。 */
public final class PageQuery {
    /** 默认页码。 */
    public static final long DEFAULT_PAGE_NUM = 1L;
    /** 默认每页记录数。 */
    public static final long DEFAULT_PAGE_SIZE = 20L;

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private long pageNum = DEFAULT_PAGE_NUM;
    private long pageSize = DEFAULT_PAGE_SIZE;
    private String orderByColumn;
    private String direction;

    /**
     * 使用给定最大页大小构建 MyBatis-Plus 分页对象。
     *
     * @param maxPageSize 最大页大小，必须大于零
     * @param <T> 记录类型
     * @return 分页对象
     */
    public <T> Page<T> build(long maxPageSize) {
        if (maxPageSize <= 0L) {
            throw new IllegalArgumentException("maxPageSize must be greater than zero");
        }
        if (pageSize > maxPageSize) {
            throw new IllegalArgumentException("pageSize must not exceed " + maxPageSize);
        }

        Page<T> page = new Page<>(pageNum, pageSize);
        List<String> columns = tokens(orderByColumn);
        if (columns.isEmpty()) {
            return page;
        }

        List<String> directions = tokens(direction);
        if (directions.isEmpty()) {
            throw new IllegalArgumentException("direction is required when orderByColumn is set");
        }
        if (directions.size() != 1 && directions.size() != columns.size()) {
            throw new IllegalArgumentException(
                    "direction count must be one or match orderByColumn count");
        }

        List<OrderItem> orders = new ArrayList<>(columns.size());
        for (int index = 0; index < columns.size(); index++) {
            String column = toSnakeCase(columns.get(index));
            String order = directions.get(directions.size() == 1 ? 0 : index)
                    .toLowerCase(Locale.ROOT);
            orders.add(switch (order) {
                case "asc", "ascending" -> OrderItem.asc(column);
                case "desc", "descending" -> OrderItem.desc(column);
                default -> throw new IllegalArgumentException(
                        "direction must be asc, desc, ascending, or descending");
            });
        }
        page.addOrder(orders);
        return page;
    }

    /**
     * 返回当前页码。
     *
     * @return 当前页码
     */
    public long getPageNum() {
        return pageNum;
    }

    /**
     * 设置当前页码。
     *
     * @param pageNum 当前页码，必须大于零
     */
    public void setPageNum(long pageNum) {
        if (pageNum <= 0L) {
            throw new IllegalArgumentException("pageNum must be greater than zero");
        }
        this.pageNum = pageNum;
    }

    /**
     * 返回每页记录数。
     *
     * @return 每页记录数
     */
    public long getPageSize() {
        return pageSize;
    }

    /**
     * 设置每页记录数。
     *
     * @param pageSize 每页记录数，必须大于零
     */
    public void setPageSize(long pageSize) {
        if (pageSize <= 0L) {
            throw new IllegalArgumentException("pageSize must be greater than zero");
        }
        this.pageSize = pageSize;
    }

    /**
     * 返回排序列列表。
     *
     * @return 逗号分隔的排序列
     */
    public String getOrderByColumn() {
        return orderByColumn;
    }

    /**
     * 设置排序列列表。
     *
     * @param orderByColumn 逗号分隔的 Java 或数据库标识符
     */
    public void setOrderByColumn(String orderByColumn) {
        validateIdentifiers(orderByColumn);
        this.orderByColumn = orderByColumn;
    }

    /**
     * 返回排序方向列表。
     *
     * @return 逗号分隔的排序方向
     */
    public String getDirection() {
        return direction;
    }

    /**
     * 设置排序方向列表。
     *
     * @param direction asc、desc、ascending 或 descending
     */
    public void setDirection(String direction) {
        for (String token : tokens(direction)) {
            String normalized = token.toLowerCase(Locale.ROOT);
            if (!List.of("asc", "desc", "ascending", "descending").contains(normalized)) {
                throw new IllegalArgumentException(
                        "direction must be asc, desc, ascending, or descending");
            }
        }
        this.direction = direction;
    }

    private static void validateIdentifiers(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String token : value.split(",", -1)) {
            String candidate = token.trim();
            if (!IDENTIFIER.matcher(candidate).matches()) {
                throw new IllegalArgumentException("invalid orderByColumn: " + candidate);
            }
        }
    }

    private static List<String> tokens(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String[] rawTokens = value.split(",", -1);
        List<String> result = new ArrayList<>(rawTokens.length);
        for (String rawToken : rawTokens) {
            String token = rawToken.trim();
            if (token.isEmpty()) {
                throw new IllegalArgumentException("list values must not contain empty tokens");
            }
            result.add(token);
        }
        return List.copyOf(result);
    }

    private static String toSnakeCase(String value) {
        StringBuilder result = new StringBuilder(value.length() + 4);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isUpperCase(current)) {
                boolean hasPrevious = index > 0;
                boolean previousLowerOrDigit = hasPrevious
                        && (Character.isLowerCase(value.charAt(index - 1))
                        || Character.isDigit(value.charAt(index - 1)));
                boolean nextLower = index + 1 < value.length()
                        && Character.isLowerCase(value.charAt(index + 1));
                if (hasPrevious && (previousLowerOrDigit || nextLower)
                        && result.charAt(result.length() - 1) != '_') {
                    result.append('_');
                }
                result.append(Character.toLowerCase(current));
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }
}
