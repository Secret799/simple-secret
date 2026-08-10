package com.ss.json.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ss.json.config.DefaultObjectMapperFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleSecretJsonModuleTest {
    private final ObjectMapper mapper = DefaultObjectMapperFactory.create();

    @Test
    void serializesSafeIntegerBoundariesAsNumbersAndOutsideAsStrings() throws Exception {
        assertEquals("9007199254740991", mapper.writeValueAsString(9007199254740991L));
        assertEquals("-9007199254740991", mapper.writeValueAsString(-9007199254740991L));
        assertEquals("\"9007199254740992\"", mapper.writeValueAsString(new BigInteger("9007199254740992")));
        assertEquals("\"-9007199254740992\"", mapper.writeValueAsString(new BigInteger("-9007199254740992")));
    }

    @Test
    void preservesDecimalAndIsoJavaTimePoliciesWithNanoseconds() throws Exception {
        assertEquals("\"12.3400\"", mapper.writeValueAsString(new BigDecimal("12.3400")));
        LocalDateTime value = LocalDateTime.of(2026, 7, 27, 14, 5, 9, 123_456_789);
        assertEquals("\"2026-07-27T14:05:09.123456789\"", mapper.writeValueAsString(value));
        assertEquals(value, mapper.readValue("\"2026-07-27T14:05:09.123456789\"", LocalDateTime.class));
        assertEquals("\"2026-07-27\"", mapper.writeValueAsString(LocalDate.of(2026, 7, 27)));
        assertEquals("\"14:05:09.123456789\"",
                mapper.writeValueAsString(LocalTime.of(14, 5, 9, 123_456_789)));
        OffsetDateTime offset = OffsetDateTime.of(value, ZoneOffset.ofHours(8));
        assertEquals("\"2026-07-27T14:05:09.123456789+08:00\"", mapper.writeValueAsString(offset));
        assertEquals(offset, mapper.readValue("\"2026-07-27T14:05:09.123456789+08:00\"", OffsetDateTime.class));
        assertEquals(TimeZone.getDefault(), mapper.getSerializationConfig().getTimeZone());
    }
}
