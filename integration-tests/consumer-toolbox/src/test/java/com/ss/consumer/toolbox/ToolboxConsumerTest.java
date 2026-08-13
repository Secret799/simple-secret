package com.ss.consumer.toolbox;

import com.ss.common.toolbox.cache.CacheRemovalCause;
import com.ss.common.toolbox.cache.ExpiringCache;
import com.ss.common.toolbox.dynamiccolumn.ColumnData;
import com.ss.common.toolbox.dynamiccolumn.ColumnProperties;
import com.ss.common.toolbox.dynamiccolumn.IDynamicColumnsService;
import com.ss.common.toolbox.dynamiccolumn.converter.ColumnDataConverter;
import com.ss.common.toolbox.time.DateTimeUnit;
import com.ss.common.toolbox.time.DurationUtils;
import com.ss.common.toolbox.time.LocalDateTimeRanges;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证第三方项目只依赖 toolbox 即可使用零第三方依赖缓存。 */
class ToolboxConsumerTest {

    @Test
    void shouldUseBomManagedToolboxWithoutExplicitVersion() throws Exception {
        Element project = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(Path.of("pom.xml").toFile())
                .getDocumentElement();
        Element dependency = dependency(project, "com.ss", "simple-secret-common-toolbox");

        assertThat(text(dependency, "version")).isNull();
    }

    @Test
    void shouldUseExpiringCacheWithoutSpringOrHutool() {
        List<CacheRemovalCause> causes = new ArrayList<>();
        try (ExpiringCache<String, String> cache =
                     new ExpiringCache<>(Duration.ofMinutes(1))) {
            cache.addRemovalListener((key, value, cause) -> causes.add(cause));
            cache.put("device", "online");
            cache.put("device", "offline");

            assertThat(cache.get("device")).isEqualTo("offline");
            assertThat(causes).containsExactly(CacheRemovalCause.REPLACED);
        }
    }

    @Test
    void shouldUseDynamicColumnContractsWithoutAdditionalDependencies() {
        TicketColumnProperties column = new TicketColumnProperties();
        column.setColumnId("priority");
        column.setBusinessType("ticket");
        column.setName("Priority");
        column.setType("integer");
        column.setOrder(1);
        TicketColumnData data = new TicketColumnData("priority", "ticket-42");
        AtomicReference<TicketColumnProperties> createdColumn = new AtomicReference<>();
        IDynamicColumnsService<TicketColumnProperties, TicketColumnData> service =
                new IDynamicColumnsService<>() {
                    @Override
                    public boolean createColumn(TicketColumnProperties receivedColumn) {
                        createdColumn.set(receivedColumn);
                        return true;
                    }
                };
        ColumnDataConverter<Integer, String> converter = new ColumnDataConverter<>() {
            @Override
            public Integer ori2db(String source) {
                return Integer.valueOf(source);
            }

            @Override
            public String db2ori(Integer target) {
                return target.toString();
            }
        };

        assertThat(service.createColumn(column)).isTrue();
        assertThat(createdColumn.get()).isSameAs(column);
        assertThat(data.columnId()).isEqualTo("priority");
        assertThat(data.businessId()).isEqualTo("ticket-42");
        assertThat(converter.ori2db("42")).isEqualTo(42);
        assertThat(converter.db2ori(42)).isEqualTo("42");
    }

    @Test
    void shouldUseTimeUtilitiesWithoutAdditionalDependencies() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 15, 12, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 2, 8, 30);

        assertThat(DurationUtils.parse("30s")).isEqualTo(Duration.ofSeconds(30L));
        assertThat(LocalDateTimeRanges.split(start, end, DateTimeUnit.MONTH))
                .hasSize(3)
                .first()
                .satisfies(range -> {
                    assertThat(range.startInclusive()).isEqualTo(start);
                    assertThat(range.endInclusive()).isEqualTo(
                            LocalDateTime.of(2026, 1, 31, 23, 59, 59, 999_999_999));
                });
    }

    private static Element dependency(Element project, String groupId, String artifactId) {
        NodeList nodes = project.getElementsByTagName("dependency");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element dependency = (Element) nodes.item(index);
            if (groupId.equals(text(dependency, "groupId"))
                    && artifactId.equals(text(dependency, "artifactId"))) {
                return dependency;
            }
        }
        throw new AssertionError("Missing dependency: " + groupId + ":" + artifactId);
    }

    private static String text(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && tagName.equals(element.getTagName())) {
                return element.getTextContent().trim();
            }
        }
        return null;
    }

    private static final class TicketColumnProperties extends ColumnProperties {
    }

    private record TicketColumnData(String columnId, String businessId) implements ColumnData {
    }
}
