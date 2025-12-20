package io.nop.core.reflect.type;

import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.parse.GenericTypeParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestGenericTypeParser {
    @Test
    public void testSimple() {
        IGenericType type = new GenericTypeParser().parseFromText(null, "String");
        assertEquals(PredefinedGenericTypes.STRING_TYPE, type);
    }

    @Test
    public void testCustom() {
        IGenericType type = new GenericTypeParser().parseFromText(null, "io.test.MyType");
        assertEquals("io.test.MyType", type.toString());
    }
}
