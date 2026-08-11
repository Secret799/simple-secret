package com.ss.mybatis.page;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/** 验证分页响应不绑定 Web 语义并持有不可变数据快照。 */
class TableDataTest {

    @Test
    void shouldCreateImmutableSnapshotFromPage() {
        ArrayList<String> records = new ArrayList<>(List.of("a", "b"));
        Page<String> page = new Page<>(1L, 20L, 8L);
        page.setRecords(records);

        TableData<String> data = TableData.from(page);
        records.add("c");

        assertThat(data.total()).isEqualTo(8L);
        assertThat(data.rows()).containsExactly("a", "b");
        assertThatNullPointerException().isThrownBy(() -> TableData.from(null));
    }
}
