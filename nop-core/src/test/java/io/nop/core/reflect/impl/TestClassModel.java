/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.convert.ITypeConverter;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.IMethodModelCollection;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.converter.ArrayTypeConverter;
import io.nop.core.type.IGenericType;
import io.nop.core.type.utils.JavaGenericTypeBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestClassModel {
    @Test
    public void testHiddenClassMethod() {
        Map<String, Object> o = new HashMap<>();
        o.put("a", 1);

        Map.Entry<String, Object> entry = o.entrySet().iterator().next();

        IClassModel classModel = ReflectionManager.instance().getClassModel(entry.getClass());
        assertNotNull(classModel.getMethodByExactType("getValue"));

    }

    @Test
    public void testMethodCollection() {
        IClassModel classModel = ReflectionManager.instance().getClassModel(String.class);
        IMethodModelCollection methods = classModel.getMethodsByName("indexOf");
        assertNull(methods.getUniqueMethod(1));
        assertNotNull(methods.getExactMatchMethod(String.class));
    }

    enum MyEnum {
        A, B;

        @StaticFactoryMethod
        static MyEnum fromText(String text) {
            return MyEnum.A;
        }
    }

    enum BaseEnum {
        A, B;
    }

    @Test
    public void testEnum() {
        IClassModel model = ReflectionManager.instance().getClassModel(MyEnum.class);
        assertNotNull(model.getFactoryMethod());
        assertEquals(MyEnum.A, model.getFactoryMethod().call1(null, "A", null));

        model = ReflectionManager.instance().getClassModel(BaseEnum.class);
        assertNotNull(model.getFactoryMethod());
        assertEquals(BaseEnum.A, model.getFactoryMethod().call1(null, "A", null));
    }

    @Test
    public void testDefaultMethods() {
        IClassModel model = ReflectionManager.instance().getClassModel(List.class);
        assertNotNull(model.getMethodsByName("stream").getUniqueMethod(0));
    }

    @Test
    public void testInterfaceExtension() {
        IClassModel model = ReflectionManager.instance()
                .getClassModel(CollectionHelper.buildImmutableList("a").getClass());
        assertNotNull(model.getMethodsByName("every"));
    }

    static class ArrayTest {
        public void test(Object[] args) {
            System.out.println(args[0]);
        }
    }

    @Test
    public void testArrayArg() {
        IGenericType type = JavaGenericTypeBuilder.buildGenericType(Object[].class);
        assertTrue(type.getRawClass().isArray());

        ITypeConverter converter = ReflectionManager.instance().getConverterForJavaType(Object[].class);
        assertTrue(converter instanceof ArrayTypeConverter);

        IClassModel model = ReflectionManager.instance().getClassModel(ArrayTest.class);
        IFunctionModel fn = model.getMethodsByName("test").getUniqueMethod();
        fn.invoke(new ArrayTest(), new Object[]{Arrays.asList("a")}, DisabledEvalScope.INSTANCE);
    }

    public interface Base {
        void f1();
    }

    public interface Base2 {
        void f2();

        void f1();
    }

    public interface MyInf extends Base, Base2 {
        void f3();
    }

    @Test
    public void testInterfaceExtends() {
        IClassModel classModel = ReflectionManager.instance().getClassModel(MyInf.class);
        assertNotNull(classModel.getMethodsByName("f1"));
        assertNotNull(classModel.getMethodsByName("f2"));
        assertNotNull(classModel.getMethodsByName("f3"));
    }

    @DisplayName("标记了Immutable注解的对象为不可变对象")
    @Test
    public void testImmutable() {
        boolean immutable = ReflectionManager.instance().getBeanModelForClass(SourceLocation.class).isImmutable();
        assertTrue(immutable);
    }

    @DataBean
    static class MyClass {
        @JsonIgnore
        public String getX() {
            return "3";
        }
    }

    @Test
    public void testJsonIgnore() {
        MyClass o = new MyClass();
        assertEquals("3", BeanTool.getComplexProperty(o, "x"));
        assertEquals("{}", JsonTool.stringify(o));
    }

    @BizModel("My")
    static class MyBizModel {
        public static final String DEFAULT_NAME = "default";

        @BizQuery
        public List<String> findPage(@Name("str") @Description("test") String str) {
            return null;
        }
    }

    static class MyExtBizModel extends MyBizModel {
        @Override
        public List<String> findPage(String str) {
            return null;
        }
    }

    @Test
    public void testInherited() {
        IClassModel classModel = ReflectionManager.instance().getClassModel(MyExtBizModel.class);
        assertEquals("default", classModel.getStaticField("DEFAULT_NAME").getValue(null));
        assertEquals("My", classModel.getAnnotation(BizModel.class).value());

        IFunctionModel fn = classModel.getMethod("findPage", 1);
        assertTrue(fn.getAnnotation(BizQuery.class) != null);

        assertEquals("str", fn.getArgs().get(0).getAnnotation(Name.class).value());
        assertEquals("test", fn.getArgs().get(0).getAnnotation(Description.class).value());
    }

    interface MyBaseType {
        String TYPE_A = "a";
    }

    interface MyExtType extends MyBaseType {
        String TYPE_B = "b";
    }

    @Test
    public void testInterfaceFields() {
        IClassModel classModel = ReflectionManager.instance().getClassModel(MyExtType.class);
        assertEquals("a", classModel.getStaticField("TYPE_A").getValue(null));
        assertEquals("b", classModel.getStaticField("TYPE_B").getValue(null));
    }
}
