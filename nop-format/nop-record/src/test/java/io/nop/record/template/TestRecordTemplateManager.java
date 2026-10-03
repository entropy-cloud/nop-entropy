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

    // 模板渲染 + JSON 解析：传入变量成为记录字段
    @Test
    public void testBuildRecordFromTemplate() {
        RecordTemplateModel model = tpl("/test/record/tpl/demo.record-template.xml");

        Map<String, Object> vars = new HashMap<>();
        vars.put("a", 5);
        vars.put("b", "x");
        Map<String, Object> record = manager.buildRecord(model, vars);
        assertEquals(5, record.get("a"));
    }

    // 回归覆盖 wi9#5（plan 2306 项 27）：传入不可变 Map（Map.of）不得抛
    // UnsupportedOperationException；prepareVars 防御性拷贝后调用方 Map 也不被回写
    @Test
    public void testBuildRecordWithImmutableVarsMap() {
        RecordTemplateModel model = tpl("/test/record/tpl/demo.record-template.xml");

        Map<String, Object> vars = Map.of("a", 5, "b", "x");
        Map<String, Object> record = manager.buildRecord(model, vars);
        assertEquals(5, record.get("a"));
        assertEquals(2, vars.size(), "调用方传入的不可变 Map 不得被写入");
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

    // plan 2306 项 38 探针：记录级 generator（表达式形式）可编译且参与合并。
    // 注意 xpl 文本域约定：以 { 起始的表达式会被当作模板节点语法拒绝，需括号包裹
    @Test
    public void testBuildRecordWithRecordLevelGenerator() {
        RecordTemplateModel model = tpl("/test/record/tpl/record-generator.record-template.xml");

        Map<String, Object> vars = new HashMap<>();
        vars.put("a", 2);
        Map<String, Object> record = manager.buildRecordWithGenerator(model, vars);
        assertEquals(2, record.get("a"));
        org.junit.jupiter.api.Assertions.assertNotNull(record.get("extra"),
                "记录级 generator 产物必须合并进记录（死代码探针）");
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
