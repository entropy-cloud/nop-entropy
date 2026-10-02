package io.nop.batch.exp;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.exp.config.TableFieldConfig;
import io.nop.commons.type.StdDataType;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.nop.batch.exp.DbToolExpConstants.VAR_INPUT;
import static io.nop.batch.exp.DbToolExpConstants.VAR_VALUE;
import static io.nop.batch.exp.DbToolExpErrors.ARG_FIELD_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FieldsProcessor 表达式求值边界：
 * 1. 字段级transformExpr在value变量上求值，来源行不可变；
 * 2. 来源值为null的字段被跳过（不写入目标行）；
 * 3. stdDataType负责类型规整，转换失败时NopException携带fieldName定位参数；
 * 4. 表级transformExpr返回Map时整体替换输出行，input变量指向来源行。
 */
public class TestFieldsProcessorEval {

    private static TableFieldConfig field(String name, String from) {
        TableFieldConfig field = new TableFieldConfig();
        field.setName(name);
        if (from != null)
            field.setFrom(from);
        return field;
    }

    private static Map<String, Object> process(FieldsProcessor processor, Map<String, Object> item) {
        BatchTaskContextImpl taskCtx = new BatchTaskContextImpl();
        IBatchChunkContext chunkCtx = taskCtx.newChunkContext();

        AtomicReference<Map<String, Object>> output = new AtomicReference<>();
        Consumer<Map<String, Object>> sink = output::set;
        processor.process(item, sink, chunkCtx);
        return output.get();
    }

    @Test
    public void testFieldTransformExprEvaluatesOnValueVar() {
        TableFieldConfig field = field("n", "raw");
        field.setTransformExpr(ctx -> {
            Integer value = (Integer) ((IEvalScope) ctx).getLocalValue(VAR_VALUE);
            return value * 2;
        });

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("raw", 21);

        Map<String, Object> out = process(new FieldsProcessor(List.of(field), null), item);

        assertEquals(42, out.get("n"));
        // 输出行只包含声明的字段，且来源行保持不变
        assertEquals(java.util.Set.of("n"), out.keySet());
        assertEquals(Map.of("raw", 21), item);
    }

    @Test
    public void testNullSourceValueSkipsField() {
        TableFieldConfig field = field("n", "raw");

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("raw", null);
        item.put("other", 1);

        Map<String, Object> out = process(new FieldsProcessor(List.of(field), null), item);

        // 来源值为null的字段不写入目标行（insert列裁剪语义）
        assertFalse(out.containsKey("n"));
        // 来源行保持不变：null值与未声明字段都原样保留
        assertEquals(2, item.size());
        assertNull(item.get("raw"));
        assertEquals(1, item.get("other"));
    }

    @Test
    public void testStdDataTypeCoercesSourceType() {
        TableFieldConfig field = field("n", "raw");
        field.setStdDataType(StdDataType.INT);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("raw", "123");

        Map<String, Object> out = process(new FieldsProcessor(List.of(field), null), item);

        assertSame(Integer.class, out.get("n").getClass());
        assertEquals(123, out.get("n"));
    }

    @Test
    public void testStdDataTypeFailureCarriesFieldName() {
        TableFieldConfig field = field("n", "raw");
        field.setStdDataType(StdDataType.INT);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("raw", "not-a-number");

        NopException ex = assertThrows(NopException.class,
                () -> process(new FieldsProcessor(List.of(field), null), item));
        assertEquals("n", ex.getParam(ARG_FIELD_NAME), "conversion failure must identify the field");
    }

    @Test
    public void testFieldWithoutFromReadsSameNameColumn() {
        // from未设置时按字段名读取来源列
        TableFieldConfig field = field("b", null);

        Map<String, Object> out = process(new FieldsProcessor(List.of(field), null), Map.of("b", 5));
        assertEquals(Map.of("b", 5), out);
    }

    @Test
    public void testTableTransformExprReplacesOutputRow() {
        TableFieldConfig field = field("n", "raw");
        AtomicReference<Object> inputVar = new AtomicReference<>();

        IEvalAction tableTransform = ctx -> {
            IEvalScope scope = (IEvalScope) ctx;
            inputVar.set(scope.getLocalValue(VAR_INPUT));
            Map<String, Object> replaced = new LinkedHashMap<>();
            replaced.put("replaced", true);
            return replaced;
        };

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("raw", 1);

        Map<String, Object> out = process(new FieldsProcessor(List.of(field), tableTransform), item);

        // 表级transformExpr返回Map时整体替换输出行
        assertEquals(Map.of("replaced", true), out);
        assertSame(item, inputVar.get(), "input variable must reference the source row");
    }
}
