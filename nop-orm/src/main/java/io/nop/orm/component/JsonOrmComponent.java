/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.component;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.graphql.GraphQLScalar;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.lang.json.IJsonSerializable;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.hook.IPropGetMissingHook;
import io.nop.core.reflect.hook.IPropSetMissingHook;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@GraphQLScalar
public class JsonOrmComponent extends AbstractOrmComponent
        implements IPropGetMissingHook, IPropSetMissingHook, IJsonSerializable {
    public static final String PROP_NAME__jsonText = "_jsonText";

    static final Object NOT_INITED = new Object();

    private Object jsonValue = NOT_INITED;

    @Override
    public void serializeToJson(IJsonHandler out) {
        out.value(null, get_jsonValue());
    }

    @Override
    public void reset() {
        jsonValue = NOT_INITED;
    }

    public String get_jsonText() {
        if (jsonValue != NOT_INITED)
            return JsonTool.stringify(jsonValue);
        return (String) internalGetPropValue(PROP_NAME__jsonText);
    }

    public void set_jsonText(String jsonText) {
        internalSetPropValue(PROP_NAME__jsonText, jsonText);
        jsonValue = NOT_INITED;
    }

    public Object get_jsonValue() {
        Object value = jsonValue;
        if (value == NOT_INITED) {
            String text = get_jsonText();
            value = jsonValue = JsonTool.parseBeanFromText(text, Object.class);
        }
        return value;
    }

    @JsonIgnore
    public boolean isJsonMap() {
        return get_jsonValue() instanceof Map;
    }

    @JsonIgnore
    public Map<String, Object> get_jsonMap() {
        return (Map<String, Object>) get_jsonValue();
    }

    public Map<String, Object> require_jsonMap() {
        Map<String, Object> map = get_jsonMap();
        if (map == null) {
            map = new LinkedHashMap<>();
            set_jsonValue(map);
        }
        return map;
    }

    public void set_jsonValue(Object jsonValue) {
        markDirty();
        this.jsonValue = jsonValue;
    }

    @JsonIgnore
    public boolean isJsonList() {
        return get_jsonValue() instanceof List;
    }

    @JsonIgnore
    public List<String> getStringList() {
        return (List<String>) get_jsonValue();
    }

    public void setStringList(List<String> list) {
        set_jsonValue(list);
    }

    public Object getValue(String name) {
        Object value = get_jsonValue();
        if (value instanceof Map)
            return ((Map<String, Object>) value).get(name);
        return null;
    }

    public void setValue(String name, Object value) {
        Object jsonValue = get_jsonValue();
        if (jsonValue instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) jsonValue;
            if (value == null) {
                map.remove(name);
            } else {
                map.put(name, value);
            }
        } else {
            // 确保json对象序列化的key顺序保持稳定
            Map<String, Object> map = new TreeMap<>();
            if (value != null)
                map.put(name, value);
            set_jsonValue(map);
        }
        markDirty();
    }

    @Override
    public void flushToEntity() {
        if (jsonValue != NOT_INITED) {
            String jsonText = JsonTool.stringify(jsonValue);
            internalSetPropValue(PROP_NAME__jsonText, jsonText);
        }
    }

    @Override
    public Set<String> prop_names() {
        Object jsonValue = get_jsonValue();
        if (jsonValue instanceof Map)
            return ((Map<String, ?>) jsonValue).keySet();
        return Collections.emptySet();
    }

    @Override
    public Object prop_get(String propName) {
        return getValue(propName);
    }

    @Override
    public boolean prop_has(String propName) {
        Object jsonValue = get_jsonValue();
        if (jsonValue instanceof Map)
            return ((Map<?, ?>) jsonValue).containsKey(propName);
        return false;
    }

    @Override
    public void prop_set(String propName, Object value) {
        setValue(propName, value);
    }
}
