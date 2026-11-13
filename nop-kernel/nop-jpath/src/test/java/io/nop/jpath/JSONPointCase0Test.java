package io.nop.jpath;

import io.nop.core.lang.json.JsonTool;
import io.nop.jpath.NopJsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;




@SuppressWarnings("unchecked")
public class JSONPointCase0Test {

    private Map json;

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        String text = "{\"foo\":[\"bar\",\"baz\"],\"pi\":3.1416,\"ext\":{\"ex1\":1,\"ex2\":\"abc\"}}";
        json = JsonTool.parseMap(text);
    }


    @Test
    void test_list() throws Exception {
        List<Object> list = (List<Object>) NopJsonPath.eval(json, "$.foo");
        assertEquals(2, list.size());
        assertEquals("bar", list.get(0));
        assertEquals("baz", list.get(1));
    }
    
    @Test
    void test_list_0() throws Exception {
        Object val = NopJsonPath.eval(json, "$.foo[0]");
        assertEquals("bar", val);
    }
    
    @Test
    void test_list_1() throws Exception {
        Object val = NopJsonPath.eval(json, "$.foo[1]");
        assertEquals("baz", val);
    }
    
    @Test
    void test_key() throws Exception {
        Object val = NopJsonPath.eval(json, "$.pi");
        assertEquals(3.1416, ((Number) val).doubleValue(), 1e-9); // JsonTool 解析浮点为 Double（原 fastjson BigDecimal 口径不适用）
    }
    
    @Test
    void test_key_1() throws Exception {
        Object val = NopJsonPath.eval(json, "$.ext.ex1");
        assertEquals(1, val);
    }
}
