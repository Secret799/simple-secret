package com.ss.ics.error;

/** 厂商 SDK 错误码的公共只读描述。 */
public interface SdkError {

    /** @return 厂商产品编码 */
    String brand();

    /** @return 厂商错误码 */
    String code();

    /** @return 不包含设备凭据的厂商错误说明 */
    String message();

    /**
     * @param prefix 不包含设备凭据的操作说明
     * @return 结构化错误文本
     */
    default String formatErrorMessage(String prefix) {
        return prefix + ", SDK brand=[" + brand() + "], code=[" + code()
                + "], message=[" + message() + "]";
    }

    /**
     * @param brand 厂商产品编码
     * @return 未定义错误描述
     */
    static SdkError undefined(String brand) {
        return new SdkError() {
            @Override
            public String brand() {
                return brand;
            }

            @Override
            public String code() {
                return "-1";
            }

            @Override
            public String message() {
                return "Unknown SDK error";
            }
        };
    }
}
