/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.CloneHelper;
import io.nop.api.core.util.IFreezable;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static io.nop.core.CoreErrors.ARG_KEY;
import static io.nop.core.CoreErrors.ARG_OLD_LOC;
import static io.nop.core.CoreErrors.ERR_JSON_FLATTEN_KEY_CONFLICT;
import static io.nop.core.CoreErrors.ERR_JSON_MAP_IS_READONLY;

public final class JObject extends AbstractMap<String, Object>
        implements IJsonContainer, IJsonSerializable {
    private transient Set<String> _keySet;
    private transient Collection<Object> _values;
    private transient Set<Entry<String, Object>> _entrySet;

    private SourceLocation loc;
    private final Map<String, ValueWithLocation> map;
    private boolean frozen;
    private String comment;

    public JObject(int size) {
        this.map = new LinkedHashMap<>(size);
    }

    public JObject() {
        this.map = new LinkedHashMap<>();
    }

    public JObject(SourceLocation loc) {
        this(loc, new LinkedHashMap<>());
    }

    public JObject(SourceLocation loc, Map<String, ValueWithLocation> map) {
        this.loc = loc;
        this.map = map;
    }

    @JsonIgnore
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @JsonIgnore
    @Override
    public SourceLocation getLocation() {
        return loc;
    }

    public void setLocation(SourceLocation loc) {
        this.loc = loc;
    }

    public String toString() {
        return "JObject[size=" + size() + ",keys=" + keySet() + "]@" + loc;
    }

    public boolean frozen() {
        return frozen;
    }

    public void freeze(boolean cascade) {
        this.frozen = true;
        if (cascade) {
            for (ValueWithLocation value : map.values()) {
                if (value.getValue() instanceof IFreezable) {
                    ((IFreezable) value.getValue()).freeze(true);
                }
            }
        }
    }

    void checkReadOnly() {
        if (frozen)
            throw new NopException(ERR_JSON_MAP_IS_READONLY);
    }

    @Override
    public void serializeToJson(IJsonHandler out) {
        out.beginObject(getLocation());
        for (Map.Entry<String, ValueWithLocation> entry : map.entrySet()) {
            ValueWithLocation vl = entry.getValue();
            out.key(entry.getKey()).value(vl.getLocation(), vl.getValue());
        }
        out.endObject();
    }

    public JObject deepClone() {
        return new JObject(loc, (Map) CloneHelper.deepClone(map));
    }

    public ValueWithLocation getLocValue(String key) {
        return map.get(key);
    }

    public SourceLocation getLocation(String key) {
        ValueWithLocation vl = map.get(key);
        return vl == null ? getLocation() : vl.getLocation();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        Set<Entry<String, Object>> ks = _entrySet;
        if (ks == null) {
            ks = new AbstractSet<Entry<String, Object>>() {
                public Iterator<Entry<String, Object>> iterator() {
                    return new Iterator<Entry<String, Object>>() {
                        private Iterator<Entry<String, ValueWithLocation>> i = map.entrySet().iterator();

                        public boolean hasNext() {
                            return i.hasNext();
                        }

                        public Entry<String, Object> next() {
                            Entry<String, ValueWithLocation> entry = i.next();
                            return new DelegateEntry(entry);
                        }

                        public void remove() {
                            checkReadOnly();
                            i.remove();
                        }
                    };
                }

                public int size() {
                    return JObject.this.size();
                }

                public boolean isEmpty() {
                    return JObject.this.isEmpty();
                }

                public void clear() {
                    JObject.this.clear();
                }

                public boolean contains(Object o) {
                    Map.Entry<String, Object> entry = (Map.Entry<String, Object>) o;
                    if (!containsKey(entry.getKey()))
                        return false;
                    return JObject.this.get(entry.getKey()) == entry.getValue();
                }
            };
            _entrySet = ks;
        }
        return ks;
    }

    static class DelegateEntry implements Map.Entry<String, Object> {
        private final Entry<String, ValueWithLocation> entry;

        public DelegateEntry(Entry<String, ValueWithLocation> entry) {
            this.entry = entry;
        }

        @Override
        public String getKey() {
            return entry.getKey();
        }

        @Override
        public Object getValue() {
            return entry.getValue().getValue();
        }

        @Override
        public Object setValue(Object value) {
            ValueWithLocation vl = entry.getValue();
            entry.setValue(ValueWithLocation.of(null, value));
            return vl.getValue();
        }

        @Override
        public boolean equals(Object o) {
            return this == o;
        }

        @Override
        public int hashCode() {
            return entry.hashCode();
        }
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }


    @Override
    public Object get(Object key) {
        ValueWithLocation value = map.get(key);
        return value == null ? null : value.getValue();
    }

    @Override
    public Object put(String key, Object value) {
        ValueWithLocation old = map.put(key, ValueWithLocation.of(null, value));
        return old == null ? null : old.getValue();
    }

    @Override
    public Object remove(Object key) {
        checkReadOnly();
        ValueWithLocation old = map.remove(key);
        return old == null ? null : old.getValue();
    }

    @Override
    public Set<String> keySet() {
        Set<String> ks = _keySet;
        if (ks == null) {
            ks = new AbstractSet<>() {
                public Iterator<String> iterator() {
                    return new Iterator<>() {
                        private Iterator<String> i = map.keySet().iterator();

                        public boolean hasNext() {
                            return i.hasNext();
                        }

                        public String next() {
                            return i.next();
                        }

                        public void remove() {
                            checkReadOnly();
                            i.remove();
                        }
                    };
                }

                public int size() {
                    return JObject.this.size();
                }

                public boolean isEmpty() {
                    return JObject.this.isEmpty();
                }

                public void clear() {
                    JObject.this.clear();
                }

                public boolean contains(Object k) {
                    return JObject.this.containsKey(k);
                }
            };
            _keySet = ks;
        }
        return ks;
    }

    @Override
    public Collection<Object> values() {
        Collection<Object> vals = _values;
        if (vals == null) {
            vals = new AbstractCollection<>() {
                public Iterator<Object> iterator() {
                    return new Iterator<>() {
                        private Iterator<ValueWithLocation> i = map.values().iterator();

                        public boolean hasNext() {
                            return i.hasNext();
                        }

                        public Object next() {
                            return i.next().getValue();
                        }

                        public void remove() {
                            checkReadOnly();
                            i.remove();
                        }
                    };
                }

                public int size() {
                    return JObject.this.size();
                }

                public boolean isEmpty() {
                    return JObject.this.isEmpty();
                }

                public void clear() {
                    JObject.this.clear();
                }

                public boolean contains(Object v) {
                    return JObject.this.containsValue(v);
                }
            };
            _values = vals;
        }
        return vals;
    }

    @Override
    public JObject cloneInstance() {
        JObject ret = new JObject(loc, new LinkedHashMap<>(map));
        return ret;
    }

    @Override
    public void forEach(BiConsumer<? super String, ? super Object> action) {
        for (Map.Entry<String, ValueWithLocation> entry : map.entrySet()) {
            action.accept(entry.getKey(), entry.getValue().getValue());
        }
    }

    public void forEachEntry(BiConsumer<String, ? super ValueWithLocation> action) {
        for (Map.Entry<String, ValueWithLocation> entry : map.entrySet()) {
            action.accept(entry.getKey(), entry.getValue());
        }
    }

    public Map<String, ValueWithLocation> flatten() {
        Map<String, ValueWithLocation> ret = new HashMap<>();
        _flattenObject(this, null, ret);
        return ret;
    }

    private void _flatten(ValueWithLocation vl, String prefix, Map<String, ValueWithLocation> ret) {
        Object value = vl.getValue();
        if (value instanceof JArray) {
            _flattenArray((JArray) value, prefix, ret);
        } else if (value instanceof JObject) {
            _flattenObject((JObject) value, prefix, ret);
        } else if (value instanceof Collection<?>) {
            _flattenCollection((Collection<?>) value, prefix, ret);
        } else if (value instanceof Map) {
            _flattenMap((Map<String, ?>) value, prefix, ret);
        } else {
            ValueWithLocation oldVl = ret.put(prefix, vl);
            if (oldVl != null)
                throw new NopException(ERR_JSON_FLATTEN_KEY_CONFLICT).loc(vl.getLocation()).param(ARG_KEY, prefix)
                        .param(ARG_OLD_LOC, oldVl.getLocation());
        }
    }

    private void _flattenObject(JObject obj, String prefix, Map<String, ValueWithLocation> ret) {
        for (Map.Entry<String, ValueWithLocation> entry : obj.map.entrySet()) {
            ValueWithLocation vl = entry.getValue();
            String name = entry.getKey();
            _flatten(vl, prefix == null ? name : prefix + "." + name, ret);
        }
    }

    private void _flattenArray(JArray array, String prefix, Map<String, ValueWithLocation> ret) {
        array.forEachEntry((vl, index) -> {
            _flatten(vl, prefix + "." + index, ret);
        });
    }

    private void _flattenCollection(Collection<?> coll, String prefix, Map<String, ValueWithLocation> ret) {
        int index = 0;
        for (Object item : coll) {
            _flatten(ValueWithLocation.of(null, item), prefix + "." + index, ret);
            index++;
        }
    }

    private void _flattenMap(Map<String, ?> map, String prefix, Map<String, ValueWithLocation> ret) {
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            String name = entry.getKey();
            _flatten(ValueWithLocation.of(null, entry.getValue()), prefix == null ? name : prefix + "." + name, ret);
        }
    }
}