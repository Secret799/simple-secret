package com.ss.common.toolbox.dynamiccolumn;

import java.util.Map;

/**
 * 保存动态列的可变属性。
 */
public class ColumnProperties {

    /**
     * 全局唯一的列标识。
     */
    private String columnId;

    /**
     * 业务类型。
     */
    private String businessType;

    /**
     * 列名称。
     */
    private String name;

    /**
     * 列类型。
     */
    private String type;

    /**
     * 附加属性。
     */
    private Map<String, Object> extra;

    /**
     * 排序序号。
     */
    private int order;

    /**
     * 创建空的列属性对象。
     */
    public ColumnProperties() {
    }

    /**
     * 获取全局唯一的列标识。
     *
     * @return 全局唯一的列标识
     */
    public String getColumnId() {
        return columnId;
    }

    /**
     * 设置全局唯一的列标识。
     *
     * @param columnId 全局唯一的列标识
     */
    public void setColumnId(String columnId) {
        this.columnId = columnId;
    }

    /**
     * 获取业务类型。
     *
     * @return 业务类型
     */
    public String getBusinessType() {
        return businessType;
    }

    /**
     * 设置业务类型。
     *
     * @param businessType 业务类型
     */
    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    /**
     * 获取列名称。
     *
     * @return 列名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置列名称。
     *
     * @param name 列名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取列类型。
     *
     * @return 列类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置列类型。
     *
     * @param type 列类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取附加属性。
     *
     * @return 附加属性映射
     */
    public Map<String, Object> getExtra() {
        return extra;
    }

    /**
     * 设置附加属性。
     *
     * @param extra 附加属性映射
     */
    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    /**
     * 获取排序序号。
     *
     * @return 排序序号
     */
    public int getOrder() {
        return order;
    }

    /**
     * 设置排序序号。
     *
     * @param order 排序序号
     */
    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * 判断对象是否属于可与当前对象比较的列属性类型。
     *
     * @param other 待比较对象
     * @return 当对象可参与相等性比较时返回 {@code true}
     */
    protected boolean canEqual(Object other) {
        return other instanceof ColumnProperties;
    }

    /**
     * 比较两个列属性对象的全部属性值。
     *
     * @param other 待比较对象
     * @return 属性值全部相等时返回 {@code true}
     */
    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof ColumnProperties)) {
            return false;
        }
        ColumnProperties that = (ColumnProperties) other;
        if (!that.canEqual(this)) {
            return false;
        }
        int thisOrder = getOrder();
        int thatOrder = that.getOrder();
        if (thisOrder != thatOrder) {
            return false;
        }
        String thisColumnId = getColumnId();
        String thatColumnId = that.getColumnId();
        if (thisColumnId != null ? !thisColumnId.equals(thatColumnId) : thatColumnId != null) {
            return false;
        }
        String thisBusinessType = getBusinessType();
        String thatBusinessType = that.getBusinessType();
        if (thisBusinessType != null ? !thisBusinessType.equals(thatBusinessType) : thatBusinessType != null) {
            return false;
        }
        String thisName = getName();
        String thatName = that.getName();
        if (thisName != null ? !thisName.equals(thatName) : thatName != null) {
            return false;
        }
        String thisType = getType();
        String thatType = that.getType();
        if (thisType != null ? !thisType.equals(thatType) : thatType != null) {
            return false;
        }
        Map<String, Object> thisExtra = getExtra();
        Map<String, Object> thatExtra = that.getExtra();
        return thisExtra != null ? thisExtra.equals(thatExtra) : thatExtra == null;
    }

    /**
     * 计算由全部属性值组成的哈希值。
     *
     * @return 属性值的哈希值
     */
    @Override
    public int hashCode() {
        int result = 1;
        int order = getOrder();
        result = result * 59 + order;
        String columnId = getColumnId();
        result = result * 59 + (columnId == null ? 43 : columnId.hashCode());
        String businessType = getBusinessType();
        result = result * 59 + (businessType == null ? 43 : businessType.hashCode());
        String name = getName();
        result = result * 59 + (name == null ? 43 : name.hashCode());
        String type = getType();
        result = result * 59 + (type == null ? 43 : type.hashCode());
        Map<String, Object> extra = getExtra();
        result = result * 59 + (extra == null ? 43 : extra.hashCode());
        return result;
    }

    /**
     * 返回包含全部属性值的字符串表示。
     *
     * @return 列属性的字符串表示
     */
    @Override
    public String toString() {
        return "ColumnProperties(columnId=" + getColumnId()
                + ", businessType=" + getBusinessType()
                + ", name=" + getName()
                + ", type=" + getType()
                + ", extra=" + getExtra()
                + ", order=" + getOrder() + ')';
    }
}
