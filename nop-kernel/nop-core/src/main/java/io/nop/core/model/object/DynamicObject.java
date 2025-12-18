/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.object;

import io.nop.api.core.annotations.core.NoReflection;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.CloneHelper;
import io.nop.api.core.util.FreezeHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.IComponentModel;
import io.nop.api.core.util.IDeepCloneable;
import io.nop.api.core.util.IMapLike;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.collections.IKeyedElement;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.lang.json.IJsonSerializable;
import io.nop.core.lang.json.handler.BuildObjectJsonHandler;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.hook.IMethodMissingHook;
import io.nop.core.reflect.hook.IPropGetMissingHook;
import io.nop.core.reflect.hook.IPropMakeMissingHook;
import io.nop.core.reflect.hook.IPropSetMissingHook;
import io.nop.core.reflect.impl.HelperMethodInvoker;
import io.nop.core.resource.component.AbstractFreezable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

import static io.nop.core.CoreErrors.ARG_ALLOWED_METHODS;
import static io.nop.core.CoreErrors.ARG_METHOD_NAME;
import static io.nop.core.CoreErrors.ARG_NAME;
import static io.nop.core.CoreErrors.ARG_NEW_VALUE;
import static io.nop.core.CoreErrors.ARG_OBJ;
import static io.nop.core.CoreErrors.ARG_OLD_VALUE;
import static io.nop.core.CoreErrors.ARG_PROP_NAME;
import static io.nop.core.CoreErrors.ARG_PROP_NAMES;
import static io.nop.core.CoreErrors.ERR_LANG_DYNAMIC_OBJECT_DUPLICATE_PROP;
import static io.nop.core.CoreErrors.ERR_LANG_DYNAMIC_OBJECT_EMPTY_PROP_NAME;
import static io.nop.core.CoreErrors.ERR_LANG_DYNAMIC_OBJECT_UNKNOWN_METHOD;
import static io.nop.core.CoreErrors.ERR_LANG_DYNAMIC_OBJECT_UNKNOWN_PROP;
import static io.nop.core.CoreErrors.ERR_REFLECT_INVALID_EXT_PROP_NAME;

/**
 * 动态构建的对象。在EL表达式中使用时与Java对象相同。
 */
public class DynamicObject extends AbstractFreezable implements IComponentModel, IMethodMissingHook,
        IPropGetMissingHook, IPropSetMissingHook, IPropMakeMissingHook, IJsonSerializable, IKeyedElement,
        IMapLike, IDeepCloneable {

    private static final AtomicLong s_seq = new AtomicLong();

    private final long seq = s_seq.incrementAndGet();

    private final Map<String, Object> propValues = new LinkedHashMap<>();
    // 有可能部分属性没有被设置
    private final Map<String, Object> defaultPropValues = new HashMap<>();
    private final Map<String, IEvalFunction> methods = new HashMap<>();

    private final String objName;
    private final String keyProp;

    public DynamicObject(String objName, String keyProp) {
        this.objName = Guard.notEmpty(objName, "objName");
        this.keyProp = keyProp;
    }

    public DynamicObject(String objName) {
        this(objName, null);
    }

    public DynamicObject deepClone() {
        DynamicObject obj = new DynamicObject(objName, keyProp);
        obj.methods.putAll(methods);
        CloneHelper.deepCloneMapTo(defaultPropValues, obj.defaultPropValues);
        CloneHelper.deepCloneMapTo(propValues, obj.propValues);
        return obj;
    }

    @Override
    public String key() {
        return StringHelper.toString(propValues.get(keyProp), null);
    }

    public String obj_name() {
        return objName;
    }

    public Map<String, Object> obj_propValues() {
        return propValues;
    }

    @NoReflection
    public SourceLocation getLocation() {
        return super.getLocation();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(objName);
        if (keyProp != null) {
            sb.append('[').append(keyProp).append('=').append(key()).append(']');
        } else if (propValues.containsKey("id")) {
            sb.append("[id=").append('=').append(propValues.get("id")).append(']');
        } else if (propValues.containsKey("name")) {
            sb.append("[name=").append('=').append(propValues.get("name")).append(']');
        }
        sb.append("@dynamic-").append(seq);
        return sb.toString();
    }

    public DynamicObject defineMethod(String methodName, IEvalFunction func, boolean useThis) {
        checkAllowChange();
        if (!useThis)
            func = new HelperMethodInvoker(func);
        methods.put(methodName, func);
        return this;
    }

    @Override
    public Map<String, Object> toMap() {
        return Collections.unmodifiableMap(propValues);
    }

    @Override
    public void freeze(boolean cascade) {
        super.freeze(cascade);
        if (cascade)
            FreezeHelper.deepFreezeObjects(propValues.values());
    }

    @Override
    public Object method_invoke(String methodName, Object[] args, IEvalScope scope) {
        IEvalFunction method = methods.get(methodName);
        if (method == null)
            throw new NopException(ERR_LANG_DYNAMIC_OBJECT_UNKNOWN_METHOD).param(ARG_METHOD_NAME, methodName)
                    .param(ARG_OBJ, this).param(ARG_ALLOWED_METHODS, methods.keySet());
        return method.invoke(this, args, scope);
    }

    @Override
    public Set<String> prop_names() {
        return propValues.keySet();
    }

    @Override
    public boolean prop_allow(String propName) {
        return propValues.containsKey(propName) || defaultPropValues.containsKey(propName) || propName.indexOf(':') > 0;
    }

    public List<Object> makeList(String propName) {
        Object value = propValues.get(propName);
        if (value == null) {
            value = new ArrayList<>();
            propValues.put(propName, value);
        }
        return (List<Object>) value;
    }

    public Map<String, Object> makeMap(String propName) {
        Object value = propValues.get(propName);
        if (value == null) {
            value = new LinkedHashMap<>();
            propValues.put(propName, value);
        }
        return (Map<String, Object>) value;
    }

    public DynamicObject makeObject(String propName) {
        Object value = propValues.get(propName);
        if (value == null) {
            value = new DynamicObject(objName + '@' + propName);
            propValues.put(propName, value);
        }
        return (DynamicObject) value;
    }

    @Override
    public Object prop_make(String propName) {
        Object value = propValues.get(propName);
        if (value == null && !propValues.containsKey(propName)) {
            value = defaultPropValues.get(propName);
            if (value != null)
                propValues.put(propName, value);
        }
        // 无法判定value类型，无法自动创建
        return value;
    }

    @Override
    public Object prop_get(String propName) {
        Object value = propValues.get(propName);

        if (value == null && !propValues.containsKey(propName)) {
            value = defaultPropValues.get(propName);
            if (value == null && !defaultPropValues.containsKey(propName)) {
                if (propName.indexOf(':') > 0)
                    return null;
                throw new NopException(ERR_LANG_DYNAMIC_OBJECT_UNKNOWN_PROP).loc(getLocation()).param(ARG_OBJ, this)
                        .param(ARG_PROP_NAME, propName).param(ARG_PROP_NAMES, getAllPropNames());
            }
        }
        return value;
    }

    private Collection<String> getAllPropNames() {
        if (defaultPropValues.isEmpty())
            return propValues.keySet();
        if (propValues.isEmpty())
            return defaultPropValues.keySet();
        Set<String> ret = new TreeSet<>();
        ret.addAll(propValues.keySet());
        ret.addAll(defaultPropValues.keySet());
        return ret;
    }

    @Override
    public boolean prop_has(String propName) {
        return propValues.containsKey(propName);
    }

    public boolean hasComplexProp(String propName) {
        int pos = propName.indexOf('.');
        if (pos <= 0) {
            return propValues.containsKey(propName);
        }
        String key = propName.substring(0, pos);
        String subProp = propName.substring(pos + 1);
        if (!propValues.containsKey(key))
            return false;

        Object value = propValues.get(key);
        if (value instanceof DynamicObject)
            return ((DynamicObject) value).hasComplexProp(subProp);
        return true;
    }

    public void makeComplexPropDefault(String propName, Object defaultValue) {
        int pos = propName.indexOf('.');
        if (pos <= 0) {
            if (!prop_has(propName)) {
                addPropDefault(propName, defaultValue);
            }
            return;
        }
        String key = propName.substring(0, pos);
        String subProp = propName.substring(pos + 1);
        Object value = prop_make(key);
        if (value instanceof DynamicObject) {
            ((DynamicObject) value).makeComplexPropDefault(subProp, defaultValue);
        }
    }

    public Object getComplexProp(String propName) {
        return BeanTool.getComplexProperty(this, propName);
    }

    public void addPropDefault(String propName, Object value) {
        checkAllowChange();

        checkPropName(propName);

        defaultPropValues.put(propName, value);
    }

    public void addProp(String propName, Object value) {
        checkAllowChange();

        checkPropName(propName);

        Object old = propValues.put(propName, value);
        if (old != null)
            throw new NopException(ERR_LANG_DYNAMIC_OBJECT_DUPLICATE_PROP).loc(getLocation()).param(ARG_OBJ, this)
                    .param(ARG_PROP_NAME, propName).param(ARG_OLD_VALUE, old).param(ARG_NEW_VALUE, value);
    }

    public void removeProp(String propName) {
        propValues.remove(propName);
    }

    private void checkPropName(String propName) {
        if (StringHelper.isEmpty(propName))
            throw new NopException(ERR_LANG_DYNAMIC_OBJECT_EMPTY_PROP_NAME).loc(getLocation()).param(ARG_OBJ, this);

        if (propName.indexOf('.') >= 0)
            throw new NopException(ERR_REFLECT_INVALID_EXT_PROP_NAME)
                    .param(ARG_NAME, propName);
    }

    @Override
    public void prop_set(String propName, Object value) {
        checkAllowChange();
        checkPropName(propName);
        propValues.put(propName, value);
    }

    public Map<String, Object> toJson() {
        BuildObjectJsonHandler handler = new BuildObjectJsonHandler();
        serializeToJson(handler);
        return (Map<String, Object>) handler.getResult();
    }

    @Override
    public void serializeToJson(IJsonHandler out) {
        out.beginObject(getLocation());
        for (Map.Entry<String, Object> entry : propValues.entrySet()) {
            Object value = entry.getValue();
            out.put(entry.getKey(), value);
        }
        out.endObject();
    }
}