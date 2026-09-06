package io.nop.biz.dev;

import io.nop.api.core.annotations.core.Description;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 参数级描述的回退逻辑：i18n未命中时应回退到参数自身的@Description，
 * 修复前错误地回退到整个函数的@Description，导致每个参数都显示函数的整体描述。
 */
public class TestDevDocBizModel {

    @Test
    public void testArgDescriptionDoesNotFallBackToFunctionDescription() {
        DevDocBizModel model = new DevDocBizModel();
        IFunctionModel fn = functionModel("myFn", "函数整体描述");

        // 参数自身没有@Description：修复前返回函数的"函数整体描述"，修复后返回null
        IFunctionArgument argWithoutDesc = argModel("arg1", null);
        assertNull(model.getDescription("zh-CN", fn, argWithoutDesc));
    }

    @Test
    public void testArgDescriptionFallsBackToArgOwnDescription() {
        DevDocBizModel model = new DevDocBizModel();
        IFunctionModel fn = functionModel("myFn", "函数整体描述");

        // 参数自身有@Description：修复前返回函数的"函数整体描述"，修复后返回参数的描述
        IFunctionArgument argWithDesc = argModel("arg1", "参数一描述");
        assertEquals("参数一描述", model.getDescription("zh-CN", fn, argWithDesc));
    }

    // ==================== fixture ====================

    static IFunctionModel functionModel(String name, String fnDescription) {
        Description desc = fnDescription == null ? null : new Description() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Description.class;
            }

            @Override
            public String value() {
                return fnDescription;
            }
        };
        return (IFunctionModel) Proxy.newProxyInstance(
                TestDevDocBizModel.class.getClassLoader(), new Class[]{IFunctionModel.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "getName":
                            return name;
                        case "getDeclaringClass":
                            return null;
                        case "getAnnotation":
                            return desc;
                        default:
                            return defaultValue(m.getReturnType());
                    }
                });
    }

    static IFunctionArgument argModel(String name, String argDescription) {
        Description desc = argDescription == null ? null : new Description() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Description.class;
            }

            @Override
            public String value() {
                return argDescription;
            }
        };
        return (IFunctionArgument) Proxy.newProxyInstance(
                TestDevDocBizModel.class.getClassLoader(), new Class[]{IFunctionArgument.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "getName":
                            return name;
                        case "getAnnotation":
                            return desc;
                        default:
                            return defaultValue(m.getReturnType());
                    }
                });
    }

    static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive())
            return null;
        if (type == boolean.class)
            return false;
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        if (type == double.class)
            return 0D;
        if (type == float.class)
            return 0F;
        if (type == short.class)
            return (short) 0;
        if (type == byte.class)
            return (byte) 0;
        if (type == char.class)
            return (char) 0;
        return null;
    }
}
