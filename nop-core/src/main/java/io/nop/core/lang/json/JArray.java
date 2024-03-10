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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

import static io.nop.core.CoreErrors.ERR_JSON_LIST_IS_READONLY;

public final class JArray extends AbstractList<Object>
        implements IJsonContainer, RandomAccess, IJsonSerializable {
    private SourceLocation loc;
    private final List<ValueWithLocation> list;
    private boolean frozen;
    private String comment;

    public JArray(int size) {
        this.list = new ArrayList<>(size);
    }

    public JArray() {
        this.list = new ArrayList<>();
    }

    public JArray(SourceLocation loc) {
        this(loc, new ArrayList<>());
    }

    private JArray(SourceLocation loc, List<ValueWithLocation> list) {
        this.loc = loc;
        this.list = list;
    }

    public static JArray singleton(Object value) {
        JArray ret = new JArray(1);
        ret.add(value);
        return ret;
    }

    @JsonIgnore
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String toString() {
        return "JArray[size=" + size() + "]@" + loc;
    }

    public JArray deepClone() {
        return new JArray(loc, (List) CloneHelper.deepClone(list));
    }

    void checkReadOnly() {
        if (frozen)
            throw new NopException(ERR_JSON_LIST_IS_READONLY);
    }

    @JsonIgnore
    public SourceLocation getLocation() {
        return loc;
    }

    public void setLocation(SourceLocation loc) {
        this.loc = loc;
    }

    public boolean frozen() {
        return frozen;
    }

    public void freeze(boolean cascade) {
        this.frozen = true;
        if (cascade) {
            for (int i = 0, n = list.size(); i < n; i++) {
                Object value = list.get(i).getValue();
                if (value instanceof IFreezable) {
                    ((IFreezable) value).freeze(cascade);
                }
            }
        }
    }

    @Override
    public void serializeToJson(IJsonHandler out) {
        out.beginArray(getLocation());
        for (int i = 0, n = list.size(); i < n; i++) {
            ValueWithLocation vl = list.get(i);
            Object value = vl.getValue();
            out.value(vl.getLocation(), value);
        }
        out.endArray();
    }

    public ValueWithLocation getLocValue(int index) {
        return list.get(index);
    }

    public SourceLocation getLocation(int index) {
        ValueWithLocation vl = list.get(index);
        return vl == null ? null : vl.getLocation();
    }

    @Override
    public Object get(int index) {
        ValueWithLocation value = list.get(index);
        if (value == null)
            return null;
        return value.getValue();
    }

    public Object first() {
        if (list.isEmpty())
            return null;
        return list.get(0);
    }

    public Object last() {
        if (list.isEmpty())
            return null;
        return list.get(list.size() - 1);
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public boolean add(Object o) {
        checkReadOnly();
        return list.add(ValueWithLocation.of(null, o));
    }

    @Override
    public Object set(int index, Object element) {
        checkReadOnly();
        ValueWithLocation value = list.set(index, ValueWithLocation.of(null, element));
        return value == null ? null : value.getValue();
    }

    @Override
    public void add(int index, Object element) {
        checkReadOnly();
        list.add(index, ValueWithLocation.of(null, element));
    }

    @Override
    public Object remove(int index) {
        checkReadOnly();
        ValueWithLocation value = list.remove(index);
        if (value != null)
            return value.getValue();
        return null;
    }

    @Override
    public int indexOf(Object o) {
        if (o instanceof ValueWithLocation)
            return list.indexOf(o);
        for (int i = 0, n = list.size(); i < n; i++) {
            ValueWithLocation value = list.get(i);
            if (Objects.equals(value.getValue(), o))
                return i;
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        if (o instanceof ValueWithLocation)
            return list.lastIndexOf(o);
        for (int i = list.size() - 1; i >= 0; i--) {
            ValueWithLocation value = list.get(i);
            if (Objects.equals(value.getValue(), o))
                return i;
        }
        return -1;
    }

    @Override
    public void clear() {
        checkReadOnly();
        list.clear();
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        if (o instanceof ValueWithLocation)
            return list.contains(o);
        for (int i = 0, n = list.size(); i < n; i++) {
            Object value = list.get(i).getValue();
            if (Objects.equals(value, o))
                return true;
        }
        return false;
    }

    @Override
    public boolean remove(Object o) {
        checkReadOnly();
        if (o instanceof ValueWithLocation) {
            return list.remove(o);
        }
        for (int i = 0, n = list.size(); i < n; i++) {
            Object value = list.get(i).getValue();
            if (Objects.equals(value, o)) {
                list.remove(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public void sort(Comparator<? super Object> c) {
        checkReadOnly();
        list.sort((v1, v2) -> {
            return c.compare(v1.getValue(), v2.getValue());
        });
    }

    @Override
    public void forEach(Consumer<? super Object> action) {
        for (ValueWithLocation value : list) {
            action.accept(value.getValue());
        }
    }

    @Override
    public JArray cloneInstance() {
        JArray ret = new JArray(loc, new ArrayList<>(list));
        return ret;
    }

    public void forEachEntry(ObjIntConsumer<? super ValueWithLocation> action) {
        for (int i = 0, n = list.size(); i < n; i++) {
            action.accept(list.get(i), i);
        }
    }
}