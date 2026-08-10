package com.ss.common.toolbox.property;

import com.ss.common.toolbox.function.SerializableFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SerializedLambdaExtractorTest {

    @Test
    void extractsThroughSerializationFallback() {
        SerializableFunction<Sample, String> getter = Sample::getValue;
        assertEquals("getValue",
                SerializedLambdaExtractor.extractUsingSerialization(getter).getImplMethodName());
    }

    static class Sample {
        private String value;

        public String getValue() {
            return value;
        }
    }
}
