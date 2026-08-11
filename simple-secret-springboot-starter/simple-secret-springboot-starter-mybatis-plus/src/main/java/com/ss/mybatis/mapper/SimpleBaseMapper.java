package com.ss.mybatis.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/** 不引入对象转换或 Join 框架的基础 Mapper 契约。 */
public interface SimpleBaseMapper<T> extends BaseMapper<T> {

    /**
     * 查询当前实体表的全部记录。
     *
     * @return 全部记录
     */
    default List<T> selectAll() {
        return selectList(new QueryWrapper<>());
    }
}
