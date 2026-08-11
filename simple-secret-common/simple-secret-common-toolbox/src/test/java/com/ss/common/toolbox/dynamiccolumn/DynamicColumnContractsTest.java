package com.ss.common.toolbox.dynamiccolumn;

import com.ss.common.toolbox.dynamiccolumn.converter.ColumnDataConverter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicColumnContractsTest {

    @Test
    void columnPropertiesExposeJavaBeanPropertiesAndValueSemantics() {
        ColumnProperties properties = new ColumnProperties();
        Map<String, Object> extra = Map.of("required", true);

        properties.setColumnId("priority");
        properties.setBusinessType("ticket");
        properties.setName("Priority");
        properties.setType("integer");
        properties.setExtra(extra);
        properties.setOrder(3);

        assertEquals("priority", properties.getColumnId());
        assertEquals("ticket", properties.getBusinessType());
        assertEquals("Priority", properties.getName());
        assertEquals("integer", properties.getType());
        assertEquals(extra, properties.getExtra());
        assertEquals(3, properties.getOrder());

        ColumnProperties sameProperties = new ColumnProperties();
        sameProperties.setColumnId("priority");
        sameProperties.setBusinessType("ticket");
        sameProperties.setName("Priority");
        sameProperties.setType("integer");
        sameProperties.setExtra(extra);
        sameProperties.setOrder(3);

        assertEquals(properties, sameProperties);
        assertEquals(properties.hashCode(), sameProperties.hashCode());
        assertNotEquals(properties, new ColumnProperties());
        assertTrue(properties.toString().contains("priority"));
    }

    @Test
    void columnPropertiesValueMethodsReadEachOverridableGetterOnce() {
        CountingColumnProperties left = countingColumnProperties();
        CountingColumnProperties right = countingColumnProperties();

        left.resetGetterCallCounts();
        right.resetGetterCallCounts();

        assertEquals(left, right);
        left.assertEachGetterCalledOnce();
        right.assertEachGetterCalledOnce();

        left.resetGetterCallCounts();

        left.hashCode();

        left.assertEachGetterCalledOnce();
    }

    @Test
    void dynamicColumnsServiceAcceptsItsSpecificColumnPropertiesType() {
        IDynamicColumnsService<CustomColumnProperties, CustomColumnData> service = column -> column.getName().equals("Priority");
        CustomColumnProperties column = new CustomColumnProperties();
        column.setName("Priority");

        assertTrue(service.createColumn(column));
    }

    @Test
    void columnDataConverterConvertsInBothDirections() {
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

        assertEquals(42, converter.ori2db("42"));
        assertEquals("42", converter.db2ori(42));
    }

    @Test
    void columnDataExposesColumnAndBusinessIdentifiers() {
        ColumnData data = new CustomColumnData("priority", "ticket-42");

        assertEquals("priority", data.columnId());
        assertEquals("ticket-42", data.businessId());
    }

    private static final class CustomColumnProperties extends ColumnProperties {
    }

    private static CountingColumnProperties countingColumnProperties() {
        CountingColumnProperties properties = new CountingColumnProperties();
        properties.setOrder(3);
        properties.setColumnId("priority");
        properties.setBusinessType("ticket");
        properties.setName("Priority");
        properties.setType("integer");
        properties.setExtra(Map.of("required", true));
        return properties;
    }

    private static final class CountingColumnProperties extends ColumnProperties {

        private int orderGetterCallCount;
        private int columnIdGetterCallCount;
        private int businessTypeGetterCallCount;
        private int nameGetterCallCount;
        private int typeGetterCallCount;
        private int extraGetterCallCount;

        @Override
        public int getOrder() {
            orderGetterCallCount++;
            return super.getOrder();
        }

        @Override
        public String getColumnId() {
            columnIdGetterCallCount++;
            return super.getColumnId();
        }

        @Override
        public String getBusinessType() {
            businessTypeGetterCallCount++;
            return super.getBusinessType();
        }

        @Override
        public String getName() {
            nameGetterCallCount++;
            return super.getName();
        }

        @Override
        public String getType() {
            typeGetterCallCount++;
            return super.getType();
        }

        @Override
        public Map<String, Object> getExtra() {
            extraGetterCallCount++;
            return super.getExtra();
        }

        private void resetGetterCallCounts() {
            orderGetterCallCount = 0;
            columnIdGetterCallCount = 0;
            businessTypeGetterCallCount = 0;
            nameGetterCallCount = 0;
            typeGetterCallCount = 0;
            extraGetterCallCount = 0;
        }

        private void assertEachGetterCalledOnce() {
            assertEquals(1, orderGetterCallCount, "getOrder");
            assertEquals(1, columnIdGetterCallCount, "getColumnId");
            assertEquals(1, businessTypeGetterCallCount, "getBusinessType");
            assertEquals(1, nameGetterCallCount, "getName");
            assertEquals(1, typeGetterCallCount, "getType");
            assertEquals(1, extraGetterCallCount, "getExtra");
        }
    }

    private record CustomColumnData(String columnId, String businessId) implements ColumnData {
    }
}
