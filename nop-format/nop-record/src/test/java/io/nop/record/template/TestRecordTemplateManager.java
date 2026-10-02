package io.nop.record.template;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.record.RecordErrors;
import io.nop.record.model.RecordTemplateModel;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RecordTemplateManager 语义：模板 + 字段变量 → JSON 记录；
 * mandatory 字段 defaultValue 兜底、缺失即报错；字段 generator 先于模板求值（buildRecordWithGenerator）。
 */
public class TestRecordTemplateManager extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    RecordTemplateManager manager = new RecordTemplateManager();

    RecordTemplateModel tpl(String path) {
        return (RecordTemplateModel) new DslModelParser("/nop/schema/record/record-template.xdef")
                .parseFromVirtualPath(path);
    }

    // 模板渲染 + JSON 解析：传入变量成为记录字段（vars 必须可写：evalScope 会原地写入字段值）
    @Test
    public void testBuildRecordFromTemplate() {
        RecordTemplateModel model = tpl("/test/record/tpl/demo.record-template.xml");

        Map<String, Object> vars = new HashMap<>();
        vars.put("a", 5);
        vars.put("b", "x");
        Map<String, Object> record = manager.buildRecord(model, vars);
        assertEquals(5, record.get("a"));
    }

    // mandatory 字段缺失时取 defaultValue；optional 字段缺失跳过注入且不报错
    @Test
    public void testMandatoryDefaultAndOptionalSkipped() {
        RecordTemplateModel model = tpl("/test/record/tpl/demo.record-template.xml");

        Map<String, Object> record = manager.buildRecord(model, new HashMap<>());
        assertEquals(1, record.get("a"));
    }

    // mandatory 字段无 defaultValue 且缺失 → ERR_RECORD_FIELD_IS_MANDATORY，并携带字段名
    @Test
    public void testMandatoryWithoutDefaultThrows() {
        RecordTemplateModel model = tpl("/test/record/tpl/mandatory.record-template.xml");

        NopException e = assertThrows(NopException.class, () -> manager.buildRecord(model, new HashMap<>()));
        assertTrue(e.getErrorCode().equals(RecordErrors.ERR_RECORD_FIELD_IS_MANDATORY.getErrorCode()));
        assertEquals("x", e.getParam("fieldName"));
    }

    // buildRecordWithGenerator：字段 generator 在模板渲染前依据 scope 变量求值
    @Test
    public void testBuildRecordWithFieldGenerator() {
        RecordTemplateModel model = tpl("/test/record/tpl/generator.record-template.xml");

        Map<String, Object> vars = new HashMap<>();
        vars.put("a", 2);
        vars.put("c", 21);
        Map<String, Object> record = manager.buildRecordWithGenerator(model, vars);
        assertEquals(2, record.get("a"));
        assertEquals(42, record.get("dbl"));
    }
}
