package io.nop.orm.support;

import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.hook.IPropGetMissingHook;
import io.nop.core.reflect.hook.IPropSetMissingHook;

import java.util.Map;
import java.util.TreeMap;

public class JsonOrmComponent extends AbstractOrmComponent
        implements IPropGetMissingHook, IPropSetMissingHook {
    public static final String PROP_NAME__jsonText = "_jsonText";

    static final Object NOT_INITED = new Object();

    private Object jsonValue = NOT_INITED;

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

    public void set_jsonValue(Object jsonValue) {
        this.jsonValue = jsonValue;
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
        orm_owner().orm_extDirty(true);
    }

    @Override
    public void flushToEntity() {
        if (jsonValue != NOT_INITED) {
            String jsonText = JsonTool.stringify(jsonValue);
            internalSetPropValue(PROP_NAME__jsonText, jsonText);
        }
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
