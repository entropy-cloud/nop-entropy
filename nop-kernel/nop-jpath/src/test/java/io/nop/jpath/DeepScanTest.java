package io.nop.jpath;

import io.nop.core.lang.json.JsonTool;
import io.nop.jpath.NopJsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


import java.util.List;

/**
 * Created by wenshao on 30/07/2017.
 */
public class DeepScanTest {
    public void test_when_deep_scanning_illegal_property_access_is_ignored() {
        Object result = NopJsonPath.eval(
                JsonTool.parseMap("{\"x\": {\"foo\": {\"bar\": 4}}, \"y\": {\"foo\": 1}}")
                , "$..foo");
        assertEquals(2, ((List) result).size());

        result = NopJsonPath.eval(
                JsonTool.parseMap("{\"x\": {\"foo\": {\"bar\": 4}}, \"y\": {\"foo\": 1}}")
                , "$..foo.bar");
        assertEquals(1, ((List) result).size());
        assertEquals(4, ((List) result).get(0));

        result = NopJsonPath.eval(
                JsonTool.parseMap("{\"x\": {\"foo\": {\"bar\": 4}}, \"y\": {\"foo\": 1}}")
                , "$..[*].foo.bar");
        assertEquals(1, ((List) result).size());
        assertEquals(4, ((List) result).get(0));

        result = NopJsonPath.eval(
                JsonTool.parseMap("{\"x\": {\"foo\": {\"baz\": 4}}, \"y\": {\"foo\": 1}}")
                , "$..[*].foo.bar");
        assertTrue(((List) result).isEmpty());
    }

}
