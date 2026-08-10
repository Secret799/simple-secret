package com.ss.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ss.json.config.DefaultObjectMapperFactory;
import com.ss.json.exception.JsonOperationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonCodecTest {
    private final JsonCodec codec = new JsonCodec(DefaultObjectMapperFactory.create());

    @Test
    void handlesObjectsBytesGenericsMapsAndLists() {
        Person person = codec.parseObject("{\"name\":\"Ada\",\"ignored\":1}", Person.class);
        assertEquals("Ada", person.name());
        assertEquals(person, codec.parseObject("{\"name\":\"Ada\"}".getBytes(StandardCharsets.UTF_8), Person.class));
        List<Person> people = codec.parseObject("[{\"name\":\"Ada\"}]", new TypeReference<>() { });
        assertEquals(List.of(person), people);
        assertEquals(List.of(person), codec.parseArray("[{\"name\":\"Ada\"}]", Person.class));
        assertEquals("Ada", codec.parseMap("{\"name\":\"Ada\"}").get("name"));
        assertEquals(1, codec.parseArrayMap("[{\"name\":\"Ada\"}]").size());
    }

    @Test
    void appliesDocumentedEmptyInputSemantics() {
        assertNull(codec.toJsonString(null));
        assertNull(codec.parseObject("  ", Person.class));
        assertNull(codec.parseObject((byte[]) null, Person.class));
        assertNull(codec.parseObject(new byte[0], Person.class));
        assertNull(codec.parseObject(" ", new TypeReference<List<Person>>() { }));
        assertNull(codec.parseMap(""));
        assertEquals(List.of(), codec.parseArray(" ", Person.class));
        assertEquals(List.of(), codec.parseArrayMap(null));
    }

    @Test
    void validatesRequiredTypesBeforeEmptyInputs() {
        assertThrows(IllegalArgumentException.class, () -> new JsonCodec(null));
        assertThrows(IllegalArgumentException.class, () -> codec.parseObject("{}", (Class<Object>) null));
        assertThrows(IllegalArgumentException.class,
                () -> codec.parseObject("{}", (TypeReference<Object>) null));
        assertThrows(IllegalArgumentException.class, () -> codec.parseObject((String) null, (Class<Object>) null));
        assertThrows(IllegalArgumentException.class, () -> codec.parseObject(" ", (Class<Object>) null));
        assertThrows(IllegalArgumentException.class, () -> codec.parseObject((byte[]) null, (Class<Object>) null));
        assertThrows(IllegalArgumentException.class, () -> codec.parseObject(new byte[0], (Class<Object>) null));
        assertThrows(IllegalArgumentException.class,
                () -> codec.parseObject((String) null, (TypeReference<Object>) null));
        assertThrows(IllegalArgumentException.class,
                () -> codec.parseObject(" ", (TypeReference<Object>) null));
        assertThrows(IllegalArgumentException.class, () -> codec.parseArray(null, null));
        assertThrows(IllegalArgumentException.class, () -> codec.parseArray(" ", null));
    }

    @Test
    void wrapsStringClassDeserializationFailureWithoutEchoingPayload() {
        String secret = "{not-json-with-password-secret}";
        assertJsonFailure(secret, "deserialize", Person.class.getName(), () -> codec.parseObject(secret, Person.class));
    }

    @Test
    void wrapsGenericDeserializationFailureWithoutEchoingPayload() {
        String secret = "[{not-json-with-password-secret}]";
        TypeReference<List<Person>> type = new TypeReference<>() { };
        assertJsonFailure(secret, "deserialize", type.getType().getTypeName(),
                () -> codec.parseObject(secret, type));
    }

    @Test
    void wrapsArrayDeserializationFailureWithoutEchoingPayload() {
        String secret = "[{not-json-with-password-secret}]";
        String target = List.class.getName() + "<" + Person.class.getName() + ">";
        assertJsonFailure(secret, "deserialize", target, () -> codec.parseArray(secret, Person.class));
    }

    @Test
    void wrapsByteArrayDeserializationFailureWithoutEchoingPayload() {
        String secret = "{not-json-with-password-secret}";
        assertJsonFailure(secret, "deserialize", Person.class.getName(),
                () -> codec.parseObject(secret.getBytes(StandardCharsets.UTF_8), Person.class));
    }

    @Test
    void wrapsSerializationFailureWithoutEchoingPayload() {
        SelfReferencingValue value = new SelfReferencingValue("serialization-password-secret");
        assertJsonFailure(value.getSecret(), "serialize", SelfReferencingValue.class.getName(),
                () -> codec.toJsonString(value));
    }

    private static void assertJsonFailure(String sensitivePayload, String operation, String target, Executable action) {
        JsonOperationException error = assertThrows(JsonOperationException.class, action);
        assertNotNull(error.getCause());
        assertTrue(error.getMessage().contains(operation));
        assertTrue(error.getMessage().contains(target));
        assertFalse(error.getMessage().contains(sensitivePayload));
        Throwable current = error;
        while (current != null) {
            assertFalse(String.valueOf(current.getMessage()).contains(sensitivePayload));
            current = current.getCause();
        }
    }

    record Person(String name) { }

    private static final class SelfReferencingValue {
        private final String secret;

        private SelfReferencingValue(String secret) {
            this.secret = secret;
        }

        public String getSecret() {
            return secret;
        }

        public SelfReferencingValue getSelf() {
            return this;
        }
    }
}
