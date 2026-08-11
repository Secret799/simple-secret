package com.ss.mqttv3.topic;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MQTT 主题校验、匹配与共享订阅工具。
 */
public final class MqttTopics {
    private static final int MATCH_CACHE_CAPACITY = 1_024;
    private static final Map<MatchKey, Boolean> MATCH_CACHE =
            new LinkedHashMap<>(128, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<MatchKey, Boolean> eldest) {
                    return size() > MATCH_CACHE_CAPACITY;
                }
            };

    private MqttTopics() {
    }

    /**
     * 校验 MQTT 订阅过滤器。
     *
     * @param filter 订阅过滤器
     * @throws IllegalArgumentException 过滤器为空或通配符位置非法
     */
    public static void validateFilter(String filter) {
        normalizeFilter(filter);
    }

    /**
     * 将原生共享订阅过滤器转换为其内部主题过滤器并完成校验。
     *
     * @param filter 普通或 {@code $share/{group}/{filter}} 过滤器
     * @return 用于本地主题匹配的内部过滤器
     */
    public static String normalizeFilter(String filter) {
        requireTopicText(filter, "Topic filter");
        String normalized = filter;
        if (filter.startsWith("$share/")) {
            String remainder = filter.substring("$share/".length());
            int separator = remainder.indexOf('/');
            if (separator <= 0 || separator == remainder.length() - 1) {
                throw new IllegalArgumentException("Shared subscription filter is invalid");
            }
            String group = remainder.substring(0, separator);
            normalized = remainder.substring(separator + 1);
            validateSharedGroup(group);
            if (normalized.startsWith("$share/")) {
                throw new IllegalArgumentException("Nested shared subscription filters are not allowed");
            }
        }
        validatePlainFilter(normalized);
        return normalized;
    }

    private static void validatePlainFilter(String filter) {
        String[] levels = filter.split("/", -1);
        for (int index = 0; index < levels.length; index++) {
            String level = levels[index];
            if (level.indexOf('#') >= 0 && (!"#".equals(level) || index != levels.length - 1)) {
                throw new IllegalArgumentException("Multi-level wildcard must occupy the final level");
            }
            if (level.indexOf('+') >= 0 && !"+".equals(level)) {
                throw new IllegalArgumentException("Single-level wildcard must occupy an entire level");
            }
        }
    }

    /**
     * 校验可用于发布的 MQTT 主题。
     *
     * @param topic 实际主题
     * @throws IllegalArgumentException 主题为空或包含通配符
     */
    public static void validateTopic(String topic) {
        requireTopicText(topic, "Topic");
        if (topic.indexOf('+') >= 0 || topic.indexOf('#') >= 0) {
            throw new IllegalArgumentException("Published topic must not contain wildcards");
        }
    }

    /**
     * 判断实际主题是否匹配订阅过滤器。
     *
     * @param filter 订阅过滤器
     * @param topic  实际主题
     * @return 匹配时返回 {@code true}
     */
    public static boolean matches(String filter, String topic) {
        String normalizedFilter = normalizeFilter(filter);
        validateTopic(topic);
        MatchKey key = new MatchKey(normalizedFilter, topic);
        synchronized (MATCH_CACHE) {
            Boolean cached = MATCH_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        boolean result = matchesValidated(normalizedFilter, topic);
        synchronized (MATCH_CACHE) {
            MATCH_CACHE.put(key, result);
        }
        return result;
    }

    /**
     * 构造 MQTT v3 共享订阅过滤器。
     *
     * @param group  共享组名称
     * @param filter 原始订阅过滤器
     * @return {@code $share/{group}/{filter}} 格式的过滤器
     */
    public static String shared(String group, String filter) {
        validateSharedGroup(group);
        if (filter != null && filter.startsWith("$share/")) {
            throw new IllegalArgumentException("Shared subscription filter is already shared");
        }
        validateFilter(filter);
        return "$share/" + group + "/" + filter;
    }

    private static void validateSharedGroup(String group) {
        if (group == null || group.isBlank() || group.indexOf('/') >= 0
                || group.indexOf('+') >= 0 || group.indexOf('#') >= 0
                || group.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("Shared subscription group is invalid");
        }
    }

    static int cacheSize() {
        synchronized (MATCH_CACHE) {
            return MATCH_CACHE.size();
        }
    }

    private static boolean matchesValidated(String filter, String topic) {
        if (topic.startsWith("$") && (filter.startsWith("#") || filter.startsWith("+"))) {
            return false;
        }
        String[] filterLevels = filter.split("/", -1);
        String[] topicLevels = topic.split("/", -1);
        int topicIndex = 0;
        for (String filterLevel : filterLevels) {
            if ("#".equals(filterLevel)) {
                return true;
            }
            if (topicIndex >= topicLevels.length) {
                return false;
            }
            if (!"+".equals(filterLevel) && !filterLevel.equals(topicLevels[topicIndex])) {
                return false;
            }
            topicIndex++;
        }
        return topicIndex == topicLevels.length;
    }

    private static void requireTopicText(String value, String label) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be empty");
        }
        if (value.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException(label + " must not contain a null character");
        }
    }

    private record MatchKey(String filter, String topic) {
    }
}
