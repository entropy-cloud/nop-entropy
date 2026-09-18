package io.nop.jq.jsonpath;

import io.nop.core.lang.json.JsonTool;
import io.nop.jq.jsonpath.NopJsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;




@SuppressWarnings("unchecked")
public class JSONPointTest_0 {

    private Map json;

    protected void setUp() throws Exception {
        String text = "{\"foo\":[\"bar\",\"baz\"],\"pi\":3.1416,\"ext\":{\"ex1\":1,\"ex2\":\"abc\"}}";
        json = JsonTool.parseMap(text);
    }

    @Test
    void test_list() throws Exception {
        List<Object> list = (List<Object>) NopJsonPath.eval(json, "/foo");
        assertEquals(2, list.size());
        assertEquals("bar", list.get(0));
        assertEquals("baz", list.get(1));
    }
    
    @Test
    void test_list_0() throws Exception {
        Object val = NopJsonPath.eval(json, "/foo/0");
        assertEquals("bar", val);
    }
    
    @Test
    void test_list_1() throws Exception {
        Object val = NopJsonPath.eval(json, "/foo/1");
        assertEquals("baz", val);
    }
    
    @Test
    void test_key() throws Exception {
        Object val = NopJsonPath.eval(json, "/pi");
        assertEquals(new BigDecimal("3.1416"), val);
    }
    
    @Test
    void test_key_1() throws Exception {
        Object val = NopJsonPath.eval(json, "/ext/ex1");
        assertEquals(1, val);
    }
}
