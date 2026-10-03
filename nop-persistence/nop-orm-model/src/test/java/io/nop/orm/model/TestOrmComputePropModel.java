/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.type.PredefinedGenericTypes;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.nop.orm.model.OrmModelErrors.ERR_ORM_COMPUTE_PROP_ARG_MISSING;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_COMPUTE_PROP_NO_GETTER;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_COMPUTE_PROP_NO_SETTER;
import static io.nop.orm.model.OrmModelErrors.ERR_ORM_UNKNOWN_COMPUTE_PROP_ARG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * WI4 覆盖补强：OrmComputePropModel 的取值/赋值/参数计算语义（纯 JUnit，lambda 充当 getter/setter）。
 */
public class TestOrmComputePropModel {

    /**
     * 模拟实体：Map 包装，提供可读写的属性
     */
    static class FakeEntity {
        final Map<String, Object> props = new HashMap<>();
    }

    static OrmComputePropModel computeProp(String name, IEvalAction getter, IEvalAction setter,
                                           OrmComputeArgModel... args) {
        OrmComputePropModel prop = new OrmComputePropModel();
        prop.setName(name);
        prop.setGetter(getter);
        prop.setSetter(setter);
        if (args.length > 0)
            prop.setArgs(new java.util.ArrayList<>(java.util.Arrays.asList(args)));

        OrmEntityModel owner = new OrmEntityModel();
        owner.setName("TestEntity");
        prop.setOwnerEntityModel(owner);
        return prop;
    }

    static OrmComputeArgModel arg(String name, io.nop.core.type.IGenericType type) {
        OrmComputeArgModel arg = new OrmComputeArgModel();
        arg.setName(name);
        arg.setType(type);
        return arg;
    }

    @Test
    public void testGetValueInvokesGetterWithEntityBound() {
        // getter 经局部变量 entity 拿到调用方实体并返回其属性
        OrmComputePropModel prop = computeProp("displayName",
                ctx -> {
                    IEvalScope scope = (IEvalScope) ctx;
                    FakeEntity entity = (FakeEntity) scope.getLocalValue(OrmModelConstants.VAR_ENTITY);
                    return "name:" + entity.props.get("name");
                }, null);

        FakeEntity entity = new FakeEntity();
        entity.props.put("name", "tom");
        assertEquals("name:tom", prop.getValue(entity));
    }

    @Test
    public void testSetValueInvokesSetterWithEntityAndValue() {
        // setter 同时拿到 entity 与 value 两个局部变量
        OrmComputePropModel prop = computeProp("displayName", null,
                ctx -> {
                    IEvalScope scope = (IEvalScope) ctx;
                    FakeEntity entity = (FakeEntity) scope.getLocalValue(OrmModelConstants.VAR_ENTITY);
                    entity.props.put("name", scope.getLocalValue(OrmModelConstants.VAR_VALUE));
                    return null;
                });

        FakeEntity entity = new FakeEntity();
        prop.setValue(entity, "jerry");
        assertEquals("jerry", entity.props.get("name"));
    }

    @Test
    public void testGetValueWithoutGetterThrowsWithModelContext() {
        // 错误必须携带实体名与属性名，便于模型定位
        OrmComputePropModel prop = computeProp("ro", null, null);

        NopException e = assertThrows(NopException.class, () -> prop.getValue(new FakeEntity()));
        assertEquals(ERR_ORM_COMPUTE_PROP_NO_GETTER.getErrorCode(), e.getErrorCode());
        assertEquals("TestEntity", e.getParam("entityName"));
        assertEquals("ro", e.getParam("propName"));
    }

    @Test
    public void testSetValueWithoutSetterThrowsWithModelContext() {
        OrmComputePropModel prop = computeProp("wo", ctx -> "x", null);

        NopException e = assertThrows(NopException.class, () -> prop.setValue(new FakeEntity(), "y"));
        assertEquals(ERR_ORM_COMPUTE_PROP_NO_SETTER.getErrorCode(), e.getErrorCode());
        assertEquals("TestEntity", e.getParam("entityName"));
        assertEquals("wo", e.getParam("propName"));
    }

    @Test
    public void testComputeValuePassesDeclaredArgsOnly() {
        // computeValue 按声明的参数名注入值并做类型转换
        OrmComputePropModel prop = computeProp("calc",
                ctx -> "v=" + ((IEvalScope) ctx).getLocalValue("flag"), null,
                arg("flag", PredefinedGenericTypes.STRING_TYPE));

        Map<String, Object> args = new HashMap<>();
        args.put("flag", "on");
        assertEquals("v=on", prop.computeValue(new FakeEntity(), args));
    }

    @Test
    public void testComputeValueUnknownArgThrows() {
        // 声明参数已提供、再传入未声明参数应报业务错误码，而不是静默忽略
        OrmComputePropModel prop = computeProp("calc",
                ctx -> "ok", null,
                arg("flag", PredefinedGenericTypes.STRING_TYPE));

        Map<String, Object> args = new HashMap<>();
        args.put("flag", "on");
        args.put("nope", "x");
        NopException e = assertThrows(NopException.class, () -> prop.computeValue(new FakeEntity(), args));
        assertEquals(ERR_ORM_UNKNOWN_COMPUTE_PROP_ARG.getErrorCode(), e.getErrorCode());
        assertEquals("TestEntity", e.getParam("entityName"));
        assertEquals("calc", e.getParam("propName"));
    }

    @Test
    public void testComputeValueNullArgsTreatedAsEmpty() {
        OrmComputePropModel prop = computeProp("calc", ctx -> "const", null);
        assertEquals("const", prop.computeValue(new FakeEntity(), null));
    }

    @Test
    public void testComputeValueMissingDeclaredArgThrowsTypedError() {
        // 回归覆盖 wi4#2（plan 2306 项 16）：声明参数缺失时必须抛带错误码与上下文的
        // NopException，而不是让 castBeanToType(null, ...) 抛裸 IllegalArgumentException
        OrmComputePropModel prop = computeProp("calc",
                ctx -> "v=" + ((IEvalScope) ctx).getLocalValue("flag"), null,
                arg("flag", PredefinedGenericTypes.STRING_TYPE));

        NopException e = assertThrows(NopException.class,
                () -> prop.computeValue(new FakeEntity(), new HashMap<>()));
        assertEquals(ERR_ORM_COMPUTE_PROP_ARG_MISSING.getErrorCode(), e.getErrorCode());
        assertEquals("TestEntity", e.getParam("entityName"));
        assertEquals("calc", e.getParam("propName"));
        assertEquals("flag", e.getParam("argName"));
    }

    @Test
    public void testDeclaredArgTypeConvertsValue() {
        // 声明为 STRING 类型的参数，输入 Integer 也会转换为字符串
        OrmComputePropModel prop = computeProp("calc",
                ctx -> "len=" + ((String) ((IEvalScope) ctx).getLocalValue("code")).length(), null,
                arg("code", PredefinedGenericTypes.STRING_TYPE));

        Map<String, Object> args = new HashMap<>();
        args.put("code", 123);
        assertEquals("len=3", prop.computeValue(new FakeEntity(), args));
    }

    @Test
    public void testStdDataTypeFollowsDeclaredType() {
        OrmComputePropModel typed = computeProp("t", null, null);
        typed.setType(PredefinedGenericTypes.STRING_TYPE);
        assertEquals(StdDataType.STRING, typed.getStdDataType());
        assertEquals("java.lang.String", typed.getJavaTypeName());

        // 未声明类型时兜底为 ANY/Object
        OrmComputePropModel untyped = computeProp("u", null, null);
        assertEquals(StdDataType.ANY, untyped.getStdDataType());
        assertEquals("java.lang.Object", untyped.getJavaTypeName());
    }

    @Test
    public void testComputePropColumnContract() {
        // 计算属性无存储列：kind 固定 COMPUTE，列相关契约返回空/否定值
        OrmComputePropModel prop = computeProp("c", null, null);

        assertEquals(OrmDataTypeKind.COMPUTE, prop.getKind());
        assertFalse(prop.isSingleColumn());
        assertFalse(prop.hasLazyLoadColumn());
        assertFalse(prop.isMandatory());
        assertEquals(-1, prop.getColumnPropId());
        assertEquals(0, prop.getColumnPropIds().length);
        assertNull(prop.getColumns());
        assertNull(prop.getAliasPropPath());
        assertNull(prop.getComment());
    }
}
