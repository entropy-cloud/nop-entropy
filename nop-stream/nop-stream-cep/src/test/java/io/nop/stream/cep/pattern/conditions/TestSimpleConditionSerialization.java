package io.nop.stream.cep.pattern.conditions;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.*;

public class TestSimpleConditionSerialization {

    private static class SerializableFilter implements io.nop.stream.core.common.functions.FilterFunction<String>, Serializable {
        private static final long serialVersionUID = 1L;
        private final String prefix;

        SerializableFilter(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public boolean filter(String value) throws Exception {
            return value != null && value.startsWith(prefix);
        }
    }

    private static class NonSerializableFilter implements io.nop.stream.core.common.functions.FilterFunction<String> {
        private final Object nonSerializableRef = new Object();

        @Override
        public boolean filter(String value) throws Exception {
            return value != null && value.startsWith("x");
        }
    }

    @Test
    public void serializableFilterRoundTrips() throws Exception {
        SimpleCondition<String> condition = SimpleCondition.of(new SerializableFilter("abc"));

        assertTrue(condition.filter("abc123"));
        assertFalse(condition.filter("xyz"));

        SimpleCondition<String> deserialized = serializeAndDeserialize(condition);
        assertTrue(deserialized.filter("abc123"));
        assertFalse(deserialized.filter("xyz"));
    }

    @Test
    public void nonSerializableFilterFailsOnSerialization() {
        SimpleCondition<String> condition = SimpleCondition.of(new NonSerializableFilter());

        assertThrows(IOException.class, () -> {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(condition);
            }
        });
    }

    @Test
    public void ofReturnsSameInstanceIfSimpleCondition() {
        SimpleCondition<String> original = new SimpleCondition<String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean filter(String value) {
                return value != null;
            }
        };

        SimpleCondition<String> result = SimpleCondition.of(original);
        assertSame(original, result, "of() should return the same instance when input is already SimpleCondition");
    }

    @SuppressWarnings("unchecked")
    private <T> T serializeAndDeserialize(T obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (T) ois.readObject();
        }
    }
}
