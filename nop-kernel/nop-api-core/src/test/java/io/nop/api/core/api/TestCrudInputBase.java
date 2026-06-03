package io.nop.api.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCrudInputBase {

    static class TestInput extends CrudInputBase {
        private String name;
        private int age;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testChgTypeField() {
        TestInput input = new TestInput();
        input.set_chgType("A");
        assertEquals("A", input.get_chgType());
    }

    @Test
    public void testJsonAnySetterAbsorbsUnknownFields() throws Exception {
        String json = "{\"name\":\"test\",\"_chgType_items\":\"A\",\"_writeMode_dept\":\"replace\"}";
        TestInput input = mapper.readValue(json, TestInput.class);

        assertEquals("test", input.getName());
        assertNull(input.get_chgType());
        assertNotNull(input.get_extAttrs());
        assertEquals("A", input.get_extAttrs().get("_chgType_items"));
        assertEquals("replace", input.get_extAttrs().get("_writeMode_dept"));
    }

    @Test
    public void testJsonAnyGetterRoundTrip() throws Exception {
        TestInput input = new TestInput();
        input.setName("test");
        input.set_chgType("U");
        input.set_extAttr("_chgType_items", "D");
        input.set_extAttr("_writeMode_dept", "merge");

        String json = mapper.writeValueAsString(input);
        assertTrue(json.contains("\"name\":\"test\""));
        assertTrue(json.contains("\"_chgType\":\"U\""));
        assertTrue(json.contains("\"_chgType_items\":\"D\""));
        assertTrue(json.contains("\"_writeMode_dept\":\"merge\""));

        TestInput roundTripped = mapper.readValue(json, TestInput.class);
        assertEquals("test", roundTripped.getName());
        assertEquals("U", roundTripped.get_chgType());
        assertEquals("D", roundTripped.get_extAttrs().get("_chgType_items"));
    }

    @Test
    public void testEmptyExtAttrsNotSerialized() throws Exception {
        TestInput input = new TestInput();
        input.setName("test");

        String json = mapper.writeValueAsString(input);
        assertTrue(!json.contains("_extAttrs"));
    }

    @Test
    public void testDeserializeToMap() throws Exception {
        TestInput input = new TestInput();
        input.setName("test");
        input.set_chgType("A");
        input.set_extAttr("_chgType_items", "U");

        @SuppressWarnings("unchecked")
        Map<String, Object> map = mapper.readValue(mapper.writeValueAsString(input), Map.class);

        assertEquals("test", map.get("name"));
        assertEquals("A", map.get("_chgType"));
        assertEquals("U", map.get("_chgType_items"));
    }
}
