package io.nop.xlang.xmeta.jsonschema;

import io.nop.commons.type.StdDataType;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.impl.ObjPropMetaImpl;
import io.nop.xlang.xmeta.impl.SchemaImpl;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 补强：XSchemaToJsonSchema（WI0 快照 0% 靶点，103 行）。
 * 验证 ISchema 到 JSON Schema 的映射语义：类型、约束、数组/对象/联合/映射结构。
 */
public class TestXSchemaToJsonSchema extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private XSchemaToJsonSchema transformer() {
        return XSchemaToJsonSchema.instance();
    }

    private static SchemaImpl simpleSchema(IGenericType type) {
        SchemaImpl schema = new SchemaImpl();
        schema.setType(type);
        return schema;
    }

    /**
     * null schema 输出空 Map，不抛异常。
     */
    @Test
    public void testNullSchemaReturnsEmptyMap() {
        Map<String, Object> ret = transformer().toJsonSchema(null, null);
        assertNotNull(ret);
        assertTrue(ret.isEmpty());
    }

    /**
     * 字符串类型映射为 type=string，min/max/maxLength/minLength/pattern 约束逐项映射。
     */
    @Test
    public void testStringSchemaWithConstraints() {
        SchemaImpl schema = simpleSchema(PredefinedGenericTypes.STRING_TYPE);
        schema.setMin(1.0);
        schema.setMax(10.0);
        schema.setMinLength(2);
        schema.setMaxLength(5);
        schema.setPattern("[a-z]+");

        Map<String, Object> ret = transformer().toJsonSchema(schema, null);
        assertEquals("string", ret.get("type"));
        assertEquals(1.0, ret.get("minimum"));
        assertEquals(10.0, ret.get("maximum"));
        assertEquals(2, ret.get("minLength"));
        assertEquals(5, ret.get("maxLength"));
        assertEquals("[a-z]+", ret.get("pattern"));
    }

    /**
     * 整数类型映射为 jsonType=number（StdDataType.INT 的 jsonType 为 number，不区分整数）。
     */
    @Test
    public void testIntegerSchema() {
        Map<String, Object> ret = transformer()
                .toJsonSchema(simpleSchema(PredefinedGenericTypes.INT_TYPE), null);
        assertEquals("number", ret.get("type"));
    }

    /**
     * 类型为 null 时缺省按 string 处理。
     */
    @Test
    public void testNullDataTypeDefaultsToString() {
        SchemaImpl schema = new SchemaImpl();
        Map<String, Object> ret = transformer().toJsonSchema(schema, null);
        assertEquals("string", ret.get("type"));
    }

    /**
     * MAP 类型映射为 additionalProperties=true 的 object，并带 patternProperties。
     */
    @Test
    public void testMapSchemaBecomesFreeFormObject() {
        Map<String, Object> ret = transformer()
                .toJsonSchema(simpleSchema(PredefinedGenericTypes.MAP_TYPE), null);
        assertEquals("object", ret.get("type"));
        assertEquals(Boolean.TRUE, ret.get("additionalProperties"));
        assertNotNull(ret.get("patternProperties"));
    }

    /**
     * DATETIME 类型映射为 type=string + format=date-time。
     */
    @Test
    public void testDateTimeFormat() {
        Map<String, Object> ret = transformer()
                .toJsonSchema(simpleSchema(PredefinedGenericTypes.DATETIME_TYPE), null);
        assertEquals(StdDataType.DATETIME.getJsonType(), ret.get("type"));
        assertEquals("date-time", ret.get("format"));
    }

    /**
     * stdDomain=email 映射为 format=email。
     */
    @Test
    public void testStdDomainEmailFormat() {
        SchemaImpl schema = simpleSchema(PredefinedGenericTypes.STRING_TYPE);
        schema.setStdDomain("email");
        Map<String, Object> ret = transformer().toJsonSchema(schema, null);
        assertEquals("email", ret.get("format"));
    }

    /**
     * list schema 映射为 type=array + items，minItems/maxItems 映射。
     */
    @Test
    public void testListSchema() {
        SchemaImpl itemSchema = simpleSchema(PredefinedGenericTypes.INT_TYPE);
        SchemaImpl listSchema = new SchemaImpl();
        listSchema.setItemSchema(itemSchema);
        listSchema.setMinItems(1);
        listSchema.setMaxItems(9);

        Map<String, Object> ret = transformer().toJsonSchema(listSchema, null);
        assertEquals("array", ret.get("type"));
        assertEquals(1, ret.get("minItems"));
        assertEquals(9, ret.get("maxItems"));
        @SuppressWarnings("unchecked")
        Map<String, Object> items = (Map<String, Object>) ret.get("items");
        assertEquals("number", items.get("type"));
    }

    /**
     * object schema：mandatory 属性进入 required，属性 schema 进入 properties。
     */
    @Test
    public void testObjectSchemaWithRequiredProps() {
        ObjPropMetaImpl mandatory = new ObjPropMetaImpl();
        mandatory.setName("name");
        mandatory.setMandatory(true);
        mandatory.setSchema(simpleSchema(PredefinedGenericTypes.STRING_TYPE));

        ObjPropMetaImpl optional = new ObjPropMetaImpl();
        optional.setName("age");
        optional.setMandatory(false);
        optional.setSchema(simpleSchema(PredefinedGenericTypes.INT_TYPE));

        SchemaImpl objSchema = new SchemaImpl();
        objSchema.setProps(Arrays.asList(mandatory, optional));
        objSchema.setMinProperties(1);
        objSchema.setMaxProperties(5);

        Map<String, Object> ret = transformer().toJsonSchema(objSchema, null);
        assertEquals("object", ret.get("type"));
        assertEquals(1, ret.get("minProperties"));
        assertEquals(5, ret.get("maxProperties"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) ret.get("properties");
        assertEquals(2, props.size());
        assertEquals("string", ((Map<?, ?>) props.get("name")).get("type"));

        assertEquals(Collections.singletonList("name"), ret.get("required"),
                "只有 mandatory 属性进入 required");
    }

    /**
     * union schema：子 schema 逐一映射后以 anyOf 聚合。
     * 回归覆盖 wi3#3（plan 2306 项 15）：anyOf 成员必须是转换后的 JSON Schema Map，
     * 而非原始 ISchema 列表。
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testUnionSchemaBecomesAnyOf() {
        SchemaImpl union = new SchemaImpl();
        union.setOneOf(Arrays.asList(
                simpleSchema(PredefinedGenericTypes.STRING_TYPE),
                simpleSchema(PredefinedGenericTypes.INT_TYPE)));

        Map<String, Object> ret = transformer().toJsonSchema(union, null);
        Object anyOf = ret.get("anyOf");
        assertNotNull(anyOf);
        List<Object> members = assertInstanceOf(List.class, anyOf).stream().toList();
        assertEquals(2, members.size());
        for (Object member : members) {
            Map<String, Object> map = assertInstanceOf(Map.class, member,
                    "anyOf 成员必须是 JSON Schema Map 而非 ISchema");
            assertNotNull(map.get("type"), "转换后的成员必须携带 type 字段");
        }
        // 两个分支类型不同：string 与 number（StdDataType.INT 的 JSON Schema 类型为 number）
        java.util.Set<Object> types = new java.util.HashSet<>();
        for (Object member : members) {
            types.add(((Map<String, Object>) member).get("type"));
        }
        assertEquals(java.util.Set.of("string", "number"), types);
    }

    /**
     * registerInstance 支持替换单例并可恢复（隔离性好，不污染其他测试）。
     */
    @Test
    public void testRegisterInstanceSwapAndRestore() {
        XSchemaToJsonSchema original = XSchemaToJsonSchema.instance();
        try {
            XSchemaToJsonSchema custom = new XSchemaToJsonSchema();
            XSchemaToJsonSchema.registerInstance(custom);
            assertEquals(custom, XSchemaToJsonSchema.instance());
        } finally {
            XSchemaToJsonSchema.registerInstance(original);
        }
        assertEquals(original, XSchemaToJsonSchema.instance());
    }

    /**
     * 未知 stdDomain 不产生 format 字段。
     */
    @Test
    public void testUnknownStdDomainHasNoFormat() {
        SchemaImpl schema = simpleSchema(PredefinedGenericTypes.STRING_TYPE);
        schema.setStdDomain("not-a-std-domain");
        Map<String, Object> ret = transformer().toJsonSchema(schema, null);
        assertNull(ret.get("format"));
    }
}
