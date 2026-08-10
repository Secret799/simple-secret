package com.ss.nats.subject;

/**
 * NATS subject 与 queue 名称校验工具。
 */
public final class NatsSubjects {

    private NatsSubjects() {
    }

    /** 校验发布主题，发布主题不允许通配符。 */
    public static void validatePublishSubject(String subject) {
        validate(subject, false, "publish subject");
    }

    /** 校验订阅主题，允许完整 token 的 {@code *} 和末尾 {@code >}。 */
    public static void validateSubscriptionSubject(String subject) {
        validate(subject, true, "subscription subject");
    }

    /** 校验队列组；空字符串表示普通订阅。 */
    public static void validateQueue(String queue) {
        if (queue == null || queue.isBlank()) {
            return;
        }
        validate(queue, false, "queue");
    }

    private static void validate(String value, boolean wildcards, String name) {
        if (value == null || value.isBlank() || value.codePoints().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("NATS " + name + " must be non-blank and contain no whitespace");
        }
        String[] tokens = value.split("\\.", -1);
        for (int index = 0; index < tokens.length; index++) {
            String token = tokens[index];
            if (token.isEmpty()) {
                throw new IllegalArgumentException("NATS " + name + " contains an empty token");
            }
            if (token.indexOf('*') >= 0 || token.indexOf('>') >= 0) {
                boolean validStar = wildcards && "*".equals(token);
                boolean validTail = wildcards && ">".equals(token) && index == tokens.length - 1;
                if (!validStar && !validTail) {
                    throw new IllegalArgumentException("NATS " + name + " contains an invalid wildcard");
                }
            }
        }
    }
}
