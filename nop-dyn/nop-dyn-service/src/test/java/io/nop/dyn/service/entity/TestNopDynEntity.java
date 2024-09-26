package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
import io.nop.dyn.dao.entity.NopDynFunctionMeta;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true,enableAppBeansFile = false, enableAutoConfig = false)
public class TestNopDynEntity extends JunitBaseTestCase {

    @Test
    public void testFunctionMeta() {
        NopDynFunctionMeta meta = new NopDynFunctionMeta();
        Map<String, Object> map = new HashMap<>();
        map.put("args", List.of(Map.of("name", "a", "type", "int")));
        meta.setFuncMeta(JsonTool.stringify(map));
        meta.setScriptLang("java");
        meta.setReturnType("boolean");
        meta.setFunctionType("query");
        meta.setSource("import java.util.Date; return a < 2;");
        String xml = meta.getSourceXml();
        System.out.println(xml);
        assertTrue(xml.contains("a:int,"));
    }
}
