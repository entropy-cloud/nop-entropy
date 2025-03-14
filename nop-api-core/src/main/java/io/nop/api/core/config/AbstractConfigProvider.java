package io.nop.api.core.config;

import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.util.StaticValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractConfigProvider implements IConfigProvider {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractConfigProvider.class);

    protected final Map<String, DefaultConfigReference<?>> usedRefs;

    private final Map<String, StaticValue<?>> staticValues;

    public AbstractConfigProvider(Map<String, DefaultConfigReference<?>> usedRefs,
                                  Map<String, StaticValue<?>> staticValues) {
        this.usedRefs = usedRefs;
        this.staticValues = staticValues;
    }

    @Override
    public Map<String, StaticValue<?>> getStaticConfigValues() {
        return staticValues;
    }

    @Override
    public Map<String, DefaultConfigReference<?>> getConfigReferences() {
        return usedRefs;
    }

    @Override
    public <T> IConfigReference<T> getStaticConfigReference(String varName, Class<T> clazz, T defaultValue, SourceLocation loc) {
        IConfigReference<T> ref = getConfigReference(varName, clazz, defaultValue, loc);
        if (!staticValues.containsKey(varName)) {
            String prop = System.getProperty(ref.getName());
            staticValues.put(varName, StaticValue.valueOf(prop));
        }
        return ref;
    }

    @Override
    public void reset() {
        usedRefs.keySet().retainAll(staticValues.keySet());
        for (DefaultConfigReference ref : usedRefs.values()) {
            StaticValue<?> value = staticValues.get(ref.getName());
            if (value != null) {
                ref.updateValue(ref.getLocation(), value.cast(ref.getName(), ref.getValueType()));
            }
        }
    }

    @Override
    public void assignConfigValue(String name, Object value) {
        Class<?> valueClass = value == null ? Object.class : value.getClass();
        DefaultConfigReference ref = makeConfigRef(null, name, valueClass);
        ref.updateValue(null, StaticValue.build(name, ref.getValueType(), value));
    }

    public <T> void updateConfigValue(IConfigReference<T> ref, T value) {
        DefaultConfigReference<T> df = makeConfigRef(null, ref.getName(), ref.getValueType());
        df.updateValue(ref.getLocation(), StaticValue.build(ref.getName(), ref.getValueType(), value));
    }


    @Override
    public <T> T getConfigValue(String varName, T defaultValue) {
        DefaultConfigReference<?> ref = getConfigRef(varName);
        Object value;
        if (ref != null) {
            value = ref.get();
        } else {
            value = System.getProperty(varName);
        }

        if (value == null)
            return defaultValue;

        if (defaultValue != null) {
            value = StaticValue.castValue(varName, defaultValue.getClass(), value);
        }
        return (T) value;
    }

    @Override
    public Map<String, Object> getConfigValueForPrefix(String prefix) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, DefaultConfigReference<?>> entry : usedRefs.entrySet()) {
            String key = entry.getKey();
            if (ApiStringHelper.startsWithConfigPrefix(key, prefix)) {
                Object value = entry.getValue().get();
                setIn(map, key, value);
            }
        }
        return map;
    }


    protected void setIn(Map<String, Object> map, String key, Object value) {
        int pos = key.indexOf('.');
        if (pos < 0) {
            map.put(key, value);
        } else {
            String prefix = key.substring(0, pos);
            String postfix = key.substring(pos + 1);
            Map<String, Object> subMap = makeSubMap(map, prefix);
            setIn(subMap, postfix, value);
        }
    }

    private Map<String, Object> makeSubMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        if (value != null) {
            LOG.warn("nop.config.key-conflict:key={},map={}", key, map);
        }
        Map<String, Object> subMap = new LinkedHashMap<>();
        map.put(key, subMap);
        return subMap;
    }

    @Override
    public <T> IConfigReference<T> getConfigReference(String varName, Class<T> clazz, T defaultValue, SourceLocation loc) {
        IConfigReference<T> ref = makeConfigRef(loc, varName, clazz);

        if (ref.getValueType() != clazz) {
            if (clazz != String.class && clazz != Object.class && !clazz.isAssignableFrom(ref.getValueType())) {
                LOG.warn("nop.config.var-type-not-unique:var={},type={},defType={}", varName, clazz, ref.getValueType());
            }

            ref = new CastTypeConfigReference<>(ref, clazz);
        }
        if (defaultValue != null) {
            ref = DefaultConfigReference.makeDefault(ref, defaultValue);
        }
        return ref;
    }

    protected DefaultConfigReference<?> getConfigRef(String varName) {
        return usedRefs.get(varName);
    }

    protected abstract <T> DefaultConfigReference<T> makeConfigRef(SourceLocation loc, String varName, Class<T> clazz);

}
