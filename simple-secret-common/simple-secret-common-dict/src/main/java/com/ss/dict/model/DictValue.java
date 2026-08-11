package com.ss.dict.model;

/**
 * 可被字典模块读取的业务字典值。
 *
 * <p>业务枚举或数据对象只需实现编码和标签，其他维度使用通用默认值。</p>
 */
public interface DictValue {

    /** 默认字典类型。 */
    String DEFAULT_TYPE = "default";

    /** 默认全局范围编码。 */
    String DEFAULT_SCOPE_CODE = "global";

    /**
     * 返回字典编码。
     *
     * @return 非空字典编码
     */
    String getDictCode();

    /**
     * 返回展示标签。
     *
     * @return 非空展示标签
     */
    String getDictLabel();

    /**
     * 返回字典类型。
     *
     * @return 字典类型
     */
    default String getDictType() {
        return DEFAULT_TYPE;
    }

    /**
     * 返回字典可见范围。
     *
     * @return 字典范围
     */
    default DictScope getDictScope() {
        return DictScope.GLOBAL;
    }

    /**
     * 返回字典范围编码。
     *
     * @return 范围编码
     */
    default String getDictScopeCode() {
        return DEFAULT_SCOPE_CODE;
    }
}
