package io.nop.jpath;

import io.nop.core.lang.json.JsonTool;
import io.nop.jpath.NopJsonPath;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;




public class JSONPointCase1Test {

    private Object json;

    @org.junit.jupiter.api.BeforeEach
    void setUp() throws Exception {
        String text = "[{\"name\":\"ljw\",\"age\":123}]";
        json = JsonTool.parse(text);
    }

    
    @Test
    void test_key_1() throws Exception {
        Object val = NopJsonPath.eval(json, "$[0].name");
        assertEquals("ljw", val);
    }
}
