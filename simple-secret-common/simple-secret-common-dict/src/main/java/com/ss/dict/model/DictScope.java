package com.ss.dict.model;

/** 字典数据的可见范围。 */
public enum DictScope {

    /** 全局共享字典。 */
    GLOBAL,

    /** 租户隔离字典。 */
    TENANT
}
