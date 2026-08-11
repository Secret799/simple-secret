package com.ss.dict.spi;

import com.ss.dict.model.DictValue;

import java.util.List;

/** 显式注册到字典 key 的数据源。 */
@FunctionalInterface
public interface DictSource {

    /**
     * 加载当前数据源的全部字典值。
     *
     * @return 字典值集合，不允许返回 {@code null}
     */
    List<? extends DictValue> load();
}
