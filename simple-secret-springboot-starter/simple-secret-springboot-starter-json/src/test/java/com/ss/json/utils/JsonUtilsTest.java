package com.ss.json.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonUtilsTest {
    @Test
    void delegatesEveryPublicOperation() {
        assertEquals("{\"name\":\"Ada\"}", JsonUtils.toJsonString(new Person("Ada")));
        assertEquals(new Person("Ada"), JsonUtils.parseObject("{\"name\":\"Ada\"}", Person.class));
        assertEquals(new Person("Ada"), JsonUtils.parseObject(
                "{\"name\":\"Ada\"}".getBytes(StandardCharsets.UTF_8), Person.class));
        assertEquals(List.of(new Person("Ada")), JsonUtils.parseObject(
                "[{\"name\":\"Ada\"}]", new TypeReference<List<Person>>() { }));
        assertEquals(List.of(new Person("Ada")), JsonUtils.parseArray("[{\"name\":\"Ada\"}]", Person.class));
        assertEquals("Ada", JsonUtils.parseMap("{\"name\":\"Ada\"}").get("name"));
        assertEquals(1, JsonUtils.parseArrayMap("[{\"name\":\"Ada\"}]").size());
    }

    @Test
    void defaultCodecIsSafeForConcurrentUse() throws Exception {
        var executor = Executors.newFixedThreadPool(8);
        try {
            Callable<Person> operation = () -> JsonUtils.parseObject(
                    JsonUtils.toJsonString(new Person("Ada")), Person.class);
            List<Callable<Person>> work = java.util.stream.IntStream.range(0, 100)
                    .mapToObj(index -> operation).toList();
            for (var future : executor.invokeAll(work)) {
                assertEquals(new Person("Ada"), future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    record Person(String name) { }
}
