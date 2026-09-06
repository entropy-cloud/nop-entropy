package io.nop.record_mapping;

import io.nop.api.core.exceptions.NopException;
import io.nop.record_mapping.impl.RecordMappingTool;
import io.nop.record_mapping.model.RecordFieldMappingConfig;
import io.nop.record_mapping.model.RecordMappingConfig;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

import static io.nop.record_mapping.RecordMappingErrors.ARG_ALLOWED_FIELD_NAMES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRecordMappingModelBasics {

    private static RecordFieldMappingConfig field(String name) {
        RecordFieldMappingConfig field = new RecordFieldMappingConfig();
        field.setName(name);
        return field;
    }

    @Test
    public void testMakeTargetObjectWithVarNameButNoTypeFallsBackToMap() {
        // 字段声明了子映射但没有 type（classModel 为 null）、无 newInstanceExpr、
        // ctx 未设 forceUseMap（服务端映射路径的默认情形）
        RecordFieldMappingConfig field = field("child");
        field.setVarName("childVar");

        RecordMappingContext ctx = new RecordMappingContext();

        // 修复前：getObjectConstructor(false,...) 返回 null，constructor.get() 直接 NPE
        Object made = RecordMappingTool.DEFAULT.makeTargetObject(field, new LinkedHashMap<>(), new LinkedHashMap<>(), ctx);

        // 兜底行为与 getItemConstructor/newTarget 一致：以 LinkedHashMap 承载子映射
        assertInstanceOf(LinkedHashMap.class, made);
        assertSame(made, ctx.getValue("childVar"));
    }

    @Test
    public void testRequireFieldReportsAllowedFieldNames() {
        RecordMappingConfig config = new RecordMappingConfig();
        config.setFields(List.of(field("a"), field("b")));

        NopException e = assertThrows(NopException.class, () -> config.requireField("c"));

        // 修复前：ARG_ALLOWED_FIELD_NAMES 误传字段数量（Integer 2），排错信息无效
        Object allowed = e.getParam(ARG_ALLOWED_FIELD_NAMES);
        assertInstanceOf(Collection.class, allowed);
        assertEquals(2, ((Collection<?>) allowed).size());
        assertTrue(((Collection<?>) allowed).contains("a"));
        assertTrue(((Collection<?>) allowed).contains("b"));
    }
}
