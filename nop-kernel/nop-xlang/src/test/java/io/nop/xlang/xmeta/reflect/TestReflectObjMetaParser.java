package io.nop.xlang.xmeta.reflect;

import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.bean.IBeanPropertyModel;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.xmeta.ISchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * WI3 补强：ReflectObjMetaParser（WI0 快照 0% 靶点，46 行）。
 * 验证 @PropMeta 注解到 ISchema 约束的映射语义，以及无注解属性返回 null。
 */
public class TestReflectObjMetaParser extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * 无 @PropMeta 注解的属性返回 null schema。
     */
    @Test
    public void testPropWithoutAnnotationReturnsNull() {
        IBeanPropertyModel propModel = propModel(PlainBean.class, "name");
        assertNull(ReflectObjMetaParser.INSTANCE.parsePropSchema(propModel));
    }

    /**
     * 数值边界注解（min/max/excludeMin）映射为 schema 约束。
     */
    @Test
    public void testRangePropMetaMapping() {
        ISchema schema = ReflectObjMetaParser.INSTANCE
                .parsePropSchema(propModel(AnnotatedBean.class, "score"));
        assertNotNull(schema);
        assertEquals(0.0, schema.getMin());
        assertEquals(100.0, schema.getMax());
        assertEquals(Boolean.TRUE, schema.getExcludeMin());
        assertNull(schema.getExcludeMax(), "注解未开启 excludeMax 时不应设置该标记");
    }

    /**
     * 字符串约束与元信息（pattern/dict/长度/描述/顺序键）映射。
     */
    @Test
    public void testStringPropMetaMapping() {
        ISchema schema = ReflectObjMetaParser.INSTANCE
                .parsePropSchema(propModel(AnnotatedBean.class, "code"));
        assertNotNull(schema);
        assertEquals("[A-Z]{3}", schema.getPattern());
        assertEquals("simple-dict", schema.getDict());
        assertEquals(3, schema.getMinLength());
        assertEquals(10, schema.getMaxLength());
        assertEquals("编码", schema.getDisplayName());
        assertEquals("code-desc", schema.getDescription());
        assertEquals("orderProp", schema.getOrderProp());
        assertEquals("keyProp", schema.getKeyProp());
    }

    /**
     * 集合与精度注解映射。
     */
    @Test
    public void testCollectionAndPrecisionMetaMapping() {
        ISchema schema = ReflectObjMetaParser.INSTANCE
                .parsePropSchema(propModel(AnnotatedBean.class, "tags"));
        assertNotNull(schema);
        assertEquals(1, schema.getMinItems());
        assertEquals(5, schema.getMaxItems());
        assertEquals(2, schema.getPrecision());
        assertEquals(1, schema.getScale());
        assertEquals(3, schema.getMultipleOf());
    }

    /**
     * buildSchemaFromPropMeta 直接从注解实例构建：stdDomain/domain 映射。
     */
    @Test
    public void testStdDomainMapping() {
        ISchema schema = ReflectObjMetaParser.INSTANCE
                .parsePropSchema(propModel(AnnotatedBean.class, "email"));
        assertNotNull(schema);
        assertEquals("email", schema.getStdDomain());
        assertEquals("my-domain", schema.getDomain());
    }

    private static IBeanPropertyModel propModel(Class<?> clazz, String propName) {
        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(clazz);
        IBeanPropertyModel propModel = beanModel.getPropertyModel(propName);
        assertNotNull(propModel, "属性模型必须存在: " + propName);
        return propModel;
    }

    static class PlainBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    static class AnnotatedBean {
        private double score;

        private String code;

        private int tags;

        private String email;

        // 注解放在 getter 上：Nop 属性模型从访问器读取注解
        @PropMeta(min = 0.0, max = 100.0, excludeMin = true)
        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        @PropMeta(pattern = "[A-Z]{3}", dict = "simple-dict", minLength = 3, maxLength = 10,
                displayName = "编码", description = "code-desc", orderProp = "orderProp", keyProp = "keyProp")
        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        @PropMeta(minItems = 1, maxItems = 5, precision = 2, scale = 1, multipleOf = 3)
        public int getTags() {
            return tags;
        }

        public void setTags(int tags) {
            this.tags = tags;
        }

        @PropMeta(stdDomain = "email", domain = "my-domain")
        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}
