/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.autotest.core.data;

import io.nop.autotest.core.AutoTestConstants;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.utils.JsonMatchHelper;

import java.lang.reflect.Method;

public class AutoTestDataHelper {
    public static Object normalizeBean(Object bean) {
        if (bean instanceof Throwable)
            return ExceptionInfo.buildFrom((Throwable) bean);
        return bean;
    }

    // public static String toJsonText(Object bean) {
    // bean = normalizeBean(bean);
    //
    // String json = JsonTool.serialize(bean, true);
    // return json;
    // }

    public static Object toJsonObject(Object bean) {
        return JsonTool.serializeToJson(bean);
    }

    public static boolean valueEquals(Object v1, Object v2) {
        return JsonMatchHelper.valueEquals(v1, v2);
    }

    public static String getTestDataPath(Class<?> testClass, Method testMethod) {
        String className = testClass.getName();
        String methodName = testMethod.getName();
        String path = className.replace('.', '/');
        return path + '/' + methodName.replace('.', '/');
    }

    public static boolean isDefaultVariant(String variant) {
        return StringHelper.isEmpty(variant) || AutoTestConstants.VARIANT_DEFAULT.equals(variant);
    }
}