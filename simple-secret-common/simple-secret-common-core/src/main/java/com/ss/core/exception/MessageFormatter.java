package com.ss.core.exception;

import java.util.Arrays;

/** JDK-only 的内部消息模板格式化器。 */
final class MessageFormatter {

    private MessageFormatter() {
    }

    static String format(String template, Object... arguments) {
        if (template == null || arguments == null || arguments.length == 0) {
            return template;
        }
        StringBuilder result = new StringBuilder(template.length() + arguments.length * 8);
        int argumentIndex = 0;
        int offset = 0;
        int placeholder;
        while (argumentIndex < arguments.length && (placeholder = template.indexOf("{}", offset)) >= 0) {
            result.append(template, offset, placeholder);
            result.append(String.valueOf(arguments[argumentIndex++]));
            offset = placeholder + 2;
        }
        result.append(template, offset, template.length());
        if (argumentIndex < arguments.length) {
            result.append(' ').append(Arrays.toString(Arrays.copyOfRange(
                    arguments, argumentIndex, arguments.length)));
        }
        return result.toString();
    }
}
