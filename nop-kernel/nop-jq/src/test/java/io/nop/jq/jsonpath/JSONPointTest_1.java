package io.nop.jq.jsonpath;

import io.nop.core.lang.json.JsonTool;
import io.nop.jq.jsonpath.NopJsonPath;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;




public class JSONPointTest_1 {

    private Object json;

    protected void setUp() throws Exception {
        String text = "[{\"name\":\"ljw\",\"age\":123}]";
        json = JsonTool.parse(text);
    }

    
    @Test
    void test_key_1() throws Exception {
        Object val = NopJsonPath.eval(json, "/0/name");
        assertEquals("ljw", val);
    }
}
