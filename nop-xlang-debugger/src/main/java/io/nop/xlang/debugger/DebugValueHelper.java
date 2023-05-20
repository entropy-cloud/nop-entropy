/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.debugger;

import io.nop.api.debugger.DebugValueKey;
import io.nop.api.debugger.DebugVariable;
import io.nop.api.debugger.LineLocation;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.ReflectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.bean.IBeanModel;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DebugValueHelper {

    public static List<DebugVariable> getExpandValue(Object value, List<DebugValueKey> keys) {
        if (keys != null) {
            for (DebugValueKey key : keys) {
                if (value == null)
                    break;
                value = getNextValue(value, key);
            }
        }
        return expandValue(value);
    }

    private static Object getNextValue(Object value, DebugValueKey key) {
        if (value.getClass().isArray()) {
            int index = key.getIndex();
            int len = Array.getLength(index);
            if (index < 0 || index >= len)
                return null;
            return Array.get(value, index);
        } else if (value instanceof Map) {
            if (key.getIndex() >= 0) {
                int index = 0;
                for (Object v : ((Map<?, ?>) value).keySet()) {
                    if (index == key.getIndex())
                        return v;
                }
                return null;
            } else {
                return ((Map<?, ?>) value).get(key.getName());
            }
        } else if (value instanceof Collection) {
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                if (key.getIndex() < 0 || key.getIndex() >= list.size())
                    return null;
                return list.get(key.getIndex());
            } else {
                int index = 0;
                for (Object item : ((Collection<?>) value)) {
                    if (index == key.getIndex())
                        return item;
                    index++;
                }
                return null;
            }
        } else {
            try {
                Field fld = getField(value.getClass(), key.getName(), key.getOwnerClass());
                if (fld == null)
                    return null;
                ReflectionHelper.makeAccessible(fld);
                return fld.get(value);
            } catch (IllegalAccessException e) {
                try {
                    return BeanTool.instance().getProperty(value, key.getName());
                } catch (Exception e2) {
                    XLangDebugger.LOG.error("nop.err.debugger.get-field-fail", e);
                    return null;
                }
            }
        }
    }

    private static Field getField(Class<?> clazz, String name, String ownerClass) {
        while (clazz != null && clazz != Object.class) {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                String fieldName = field.getName();
                if (fieldName.equals(name)) {
                    if (ownerClass == null || field.getDeclaringClass().getTypeName().equals(ownerClass))
                        return field;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static List<DebugVariable> expandValue(Object value) {
        if (value == null)
            return Collections.emptyList();

        StdDataType dataType = StdDataType.fromJavaClass(value.getClass());
        if (dataType != null && dataType.isSimpleType())
            return Collections.emptyList();

        List<DebugVariable> ret = new ArrayList<>();

        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            int maxSize = 100;
            for (int i = 0; i < maxSize && i < len; i++) {
                Object item = Array.get(value, i);
                DebugVariable var = buildDebugVariable("[" + i + "]", item, null);
                var.setIndex(i);
                var.setType(value.getClass().getComponentType().getTypeName());
                ret.add(var);
            }
            if (len > maxSize) {
                DebugVariable var = new DebugVariable();
                var.setKind("more");
                var.setIndex(maxSize);
                ret.add(var);
            }
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String name = String.valueOf(entry.getKey());
                DebugVariable var = buildDebugVariable(name, entry.getValue(), null);
                if (entry.getValue() instanceof String) {
                    var.setIndex(-1);
                } else {
                    var.setIndex(index);
                }
                ret.add(var);
                index++;
            }
        } else if (value instanceof Collection) {
            int index = 0;
            for (Object item : ((Collection<?>) value)) {
                DebugVariable var = buildDebugVariable("[" + index + "]", item, null);
                var.setIndex(index);
                ret.add(var);
                index++;
            }
        } else {
            try {
                Class<?> clazz = value.getClass();
                while (clazz != Object.class && clazz != null) {
                    Field[] fields = clazz.getDeclaredFields();
                    for (Field field : fields) {
                        if (Modifier.isStatic(field.getModifiers()))
                            continue;
                        if (Modifier.isNative(field.getModifiers()))
                            continue;

                        String name = field.getName();
                        ReflectionHelper.makeAccessible(field);
                        Object v = field.get(value);
                        DebugVariable var = buildDebugVariable(name, v, null);
                        var.setOwnerClass(clazz.getTypeName());
                        var.setScope(getFieldScope(field));
                        var.setKind("field");
                        ret.add(var);
                    }
                    clazz = clazz.getSuperclass();
                }
            } catch (IllegalAccessException e) {
                // ignore error
                IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(value.getClass());
                beanModel.forEachReadableProp(prop -> {
                    try {
                        Object v = prop.getPropertyValue(value);
                        DebugVariable var = buildDebugVariable(prop.getName(), v, null);
                        var.setKind("prop");
                        ret.add(var);
                    } catch (Exception e2) {
                        DebugVariable var = buildDebugVariable(prop.getName(), e2.toString(), null);
                        var.setType(e2.getClass().getTypeName());
                        var.setKind("error");
                        ret.add(var);
                    }
                });
            }
        }

        return ret;
    }

    private static String getFieldScope(Field field) {
        if (Modifier.isPublic(field.getModifiers()))
            return "public";
        if (Modifier.isProtected(field.getModifiers()))
            return "protected";
        return "private";
    }

    public static DebugVariable buildDebugVariable(String name, Object value, LineLocation line) {
        DebugVariable var = new DebugVariable();
        var.setName(name);
        if (value != null)
            var.setHash(System.identityHashCode(value));
        var.setValue(valueToString(value));
        var.setType(valueType(value));
        var.setAssignLoc(line);
        return var;
    }

    static String valueType(Object value) {
        return value == null ? null : value.getClass().getTypeName();
    }

    static String valueToString(Object value) {
        if (value == null)
            return null;

        String str = StringHelper.safeToString(value);
        if (str.length() > 100) {
            str = str.substring(0, 100) + "...";
        }
        return str;
    }
}
