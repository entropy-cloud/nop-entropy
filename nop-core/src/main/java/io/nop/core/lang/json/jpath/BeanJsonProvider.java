/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.jpath;

import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.spi.json.JsonProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.IoHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.hook.IPropGetMissingHook;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BeanJsonProvider implements JsonProvider {
    public static BeanJsonProvider INSTANCE = new BeanJsonProvider();

    @Override
    public Object parse(String s) throws InvalidJsonException {
        return JsonTool.parse(s);
    }

    @Override
    public Object parse(InputStream inputStream, String charset) throws InvalidJsonException {
        try {
            String text = IoHelper.readText(inputStream, charset);
            return JsonTool.parse(text);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public String toJson(Object o) {
        return JsonTool.stringify(o);
    }

    @Override
    public Object createArray() {
        return new ArrayList<>();
    }

    @Override
    public Object createMap() {
        return new LinkedHashMap<>();
    }

    @Override
    public boolean isArray(Object o) {
        if (o == null)
            return false;
        return o instanceof Collection || o.getClass().isArray();
    }

    @Override
    public int length(Object o) {
        if (o instanceof Collection)
            return ((Collection<?>) o).size();
        if (o.getClass().isArray())
            return Array.getLength(o);
        return 0;
    }

    @Override
    public Iterable<?> toIterable(Object o) {
        if (o instanceof Iterable)
            return (Iterable<?>) o;
        return () -> CollectionHelper.toIterator(o, false);
    }

    @Override
    public Collection<String> getPropertyKeys(Object o) {
        if (o instanceof Class)
            return Collections.emptySet();

        if (o instanceof Map)
            return ((Map<String, ?>) o).keySet();

        if (o instanceof DynamicObject) {
            return ((DynamicObject) o).prop_names();
        }

        Set<String> keySet = ReflectionManager.instance().getBeanModelForClass(o.getClass()).getPropertyModels()
                .keySet();
        if (o instanceof IPropGetMissingHook) {
            Set<String> extProps = ((IPropGetMissingHook) o).prop_names();
            return CollectionHelper.mergeSet(keySet, extProps);
        }
        return keySet;
    }

    @Override
    public Object getArrayIndex(Object o, int i) {
        return getArrayIndex(o, i, true);
    }

    @Override
    public Object getArrayIndex(Object o, int i, boolean unwrap) {
        if (o.getClass().isArray())
            return Array.get(o, i);

        List<Object> list = (List<Object>) o;
        return list.get(i);
    }

    @Override
    public void setArrayIndex(Object o, int i, Object value) {
        if (o.getClass().isArray()) {
            Array.set(o, i, value);
        } else {
            List<Object> list = (List<Object>) o;
            if (list.size() == i) {
                list.add(value);
            } else {
                list.set(i, value);
            }
        }
    }

    @Override
    public Object getMapValue(Object o, String name) {
        if (o instanceof Map)
            return ((Map) o).get(name);
        return BeanTool.instance().getProperty(o, name);
    }

    @Override
    public void setProperty(Object o, Object key, Object value) {
        if (o instanceof Map) {
            ((Map) o).put(key, value);
        } else {
            BeanTool.instance().setProperty(o, (String) key, value);
        }
    }

    @Override
    public void removeProperty(Object o, Object key) {
        if (o instanceof Map)
            ((Map) o).remove(key);
    }

    @Override
    public boolean isMap(Object o) {
        if (o == null)
            return false;
        if (isArray(o))
            return false;

        return !StdDataType.isSimpleType(o.getClass().getName());
    }

    @Override
    public Object unwrap(Object o) {
        return o;
    }
}
