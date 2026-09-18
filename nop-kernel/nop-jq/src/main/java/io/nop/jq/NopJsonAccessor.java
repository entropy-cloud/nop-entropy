package io.nop.jq;

import io.nop.commons.type.StdDataType;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JsonAccessor implementation that delegates to nop-core's BeanTool and ReflectionManager.
 * Handles Map, Collection, arrays, and JavaBean objects.
 */
public class NopJsonAccessor implements JsonAccessor {
    public static final NopJsonAccessor INSTANCE = new NopJsonAccessor();

    @Override
    public Object getProperty(Object target, String propName) {
        if (target == null)
            return null;
        if (target instanceof Map) {
            return ((Map<?, ?>) target).get(propName);
        }
        return BeanTool.instance().getProperty(target, propName);
    }

    @Override
    public void setProperty(Object target, String propName, Object value) {
        if (target instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) target;
            map.put(propName, value);
        } else {
            BeanTool.instance().setProperty(target, propName, value);
        }
    }

    @Override
    public boolean hasProperty(Object target, String propName) {
        if (target == null)
            return false;
        if (target instanceof Map) {
            return ((Map<?, ?>) target).containsKey(propName);
        }
        return BeanTool.instance().hasProperty(target, propName);
    }

    @Override
    public Set<String> getPropertyNames(Object target) {
        if (target instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) target;
            return map.keySet();
        }
        return ReflectionManager.instance().getBeanModelForClass(target.getClass())
                .getPropertyModels().keySet();
    }

    @Override
    public boolean isMap(Object target) {
        if (target == null)
            return false;
        if (isArray(target))
            return false;
        return !StdDataType.isSimpleType(target.getClass().getName());
    }

    @Override
    public boolean isArray(Object target) {
        return target instanceof Collection || (target != null && target.getClass().isArray());
    }

    @Override
    public int size(Object target) {
        if (target instanceof Map)
            return ((Map<?, ?>) target).size();
        if (target instanceof Collection)
            return ((Collection<?>) target).size();
        if (target != null && target.getClass().isArray())
            return Array.getLength(target);
        return 0;
    }

    @Override
    public Object getArrayItem(Object target, int index) {
        if (target instanceof List) {
            return ((List<?>) target).get(index);
        }
        if (target instanceof Collection) {
            int i = 0;
            for (Object item : (Collection<?>) target) {
                if (i == index)
                    return item;
                i++;
            }
            return null;
        }
        if (target != null && target.getClass().isArray()) {
            return Array.get(target, index);
        }
        return null;
    }

    @Override
    public void setArrayItem(Object target, int index, Object value) {
        if (target instanceof List) {
            ((List<Object>) target).set(index, value);
        } else if (target != null && target.getClass().isArray()) {
            Array.set(target, index, value);
        }
    }

    @Override
    public String getTypeName(Object target) {
        if (target == null)
            return "null";
        if (target instanceof Map)
            return "object";
        if (target instanceof Collection || target.getClass().isArray())
            return "array";
        if (target instanceof String)
            return "string";
        if (target instanceof Boolean)
            return "boolean";
        if (target instanceof Number)
            return "number";
        return target.getClass().getSimpleName();
    }
}
