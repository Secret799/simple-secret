package com.ss.mybatis.page;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** 验证分页参数、排序转换和 SQL 注入防护。 */
class PageQueryTest {

    @Test
    void shouldBuildPageWithSafeDefaults() {
        Page<Object> page = new PageQuery().build(500L);

        assertThat(page.getCurrent()).isEqualTo(1L);
        assertThat(page.getSize()).isEqualTo(20L);
        assertThat(page.orders()).isEmpty();
    }

    @Test
    void shouldConvertCamelCaseAndApplyPerColumnDirections() {
        PageQuery query = new PageQuery();
        query.setPageNum(2L);
        query.setPageSize(50L);
        query.setOrderByColumn("createdAt,user_id");
        query.setDirection("descending,ASC");

        Page<Object> page = query.build(100L);

        assertThat(page.getCurrent()).isEqualTo(2L);
        assertThat(page.getSize()).isEqualTo(50L);
        assertThat(page.orders()).extracting(OrderItem::getColumn)
                .containsExactly("created_at", "user_id");
        assertThat(page.orders()).extracting(OrderItem::isAsc)
                .containsExactly(false, true);
    }

    @Test
    void shouldApplySingleDirectionToEveryColumn() {
        PageQuery query = new PageQuery();
        query.setOrderByColumn("id,createTime");
        query.setDirection("asc");

        assertThat(query.build(500L).orders()).extracting(OrderItem::isAsc)
                .containsExactly(true, true);
    }

    @Test
    void shouldRejectInvalidPaginationAndSortInputs() {
        PageQuery query = new PageQuery();

        assertThatIllegalArgumentException().isThrownBy(() -> query.setPageNum(0L));
        assertThatIllegalArgumentException().isThrownBy(() -> query.setPageSize(-1L));

        query.setPageSize(501L);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> query.build(500L))
                .withMessage("pageSize must not exceed 500");

        for (String unsafe : List.of(
                "name desc", "name;drop", "user.name", "count(id)", "id,--name", "id,")) {
            PageQuery unsafeQuery = new PageQuery();
            assertThatIllegalArgumentException()
                    .as("unsafe order column: %s", unsafe)
                    .isThrownBy(() -> unsafeQuery.setOrderByColumn(unsafe));
        }
    }

    @Test
    void shouldRejectDirectionCountMismatch() {
        PageQuery query = new PageQuery();
        query.setOrderByColumn("id,createTime");
        query.setDirection("asc,desc,asc");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> query.build(500L))
                .withMessage("direction count must be one or match orderByColumn count");
    }
}
