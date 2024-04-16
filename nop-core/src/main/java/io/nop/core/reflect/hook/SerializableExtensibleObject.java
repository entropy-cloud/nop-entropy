/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.hook;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.reflect.bean.BeanTool;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static io.nop.core.CoreErrors.ARG_NAME;
import static io.nop.core.CoreErrors.ARG_OBJ;
import static io.nop.core.CoreErrors.ARG_PROP;
import static io.nop.core.CoreErrors.ERR_REFLECT_INVALID_EXT_PROP_NAME;
import static io.nop.core.CoreErrors.ERR_REFLECT_UNKNOWN_PROP;

public class SerializableExtensibleObject implements IExtensibleObject {
    private static final long serialVersionUID = -7000895782260173940L;
    private Map<String, Object> extProps = Collections.emptyMap();

    public void copyExtPropsTo(SerializableExtensibleObject obj) {
        obj.setExtProps(extProps);
    }

    public void mergeExtPropsIfAbsent(SerializableExtensibleObject obj) {
        obj.extProps.forEach((name, value) -> {
            if (!hasExtProp(name)) {
                setExtProp(name, value);
            }
        });
    }

    public void readExtProps(String prefix, boolean removePrefix, Object bean) {
        extProps.forEach((name, value) -> {
            if (name.startsWith(prefix)) {
                if (removePrefix)
                    name = name.substring(prefix.length());
                BeanTool.setProperty(bean, name, value);
            }
        });
    }

    public void setExtProps(Map<String, Object> extProps) {
        if (extProps != null && !extProps.isEmpty()) {
            for (Map.Entry<String, Object> entry : extProps.entrySet()) {
                setExtProp(entry.getKey(), entry.getValue());
            }
        }
    }

    public boolean hasExtProp(String name) {
        if (extProps.containsKey(name))
            return true;
        return false;
    }

    public boolean isAllowedExtProp(String name) {
        boolean b = extProps.containsKey(name);
        if (!b) {
            if (name.indexOf(':') > 0 || name.indexOf('-') > 0)
                return true;
        }
        return b;
    }

    public Object getExtProp(String name) {
        return extProps.get(name);
    }

    public void setExtProp(String name, Object value) {
        checkAllowChange();
        Guard.notNull(name, "name of ext prop");
        if (name.indexOf('.') >= 0)
            throw new NopException(ERR_REFLECT_INVALID_EXT_PROP_NAME)
                    .param(ARG_NAME, name);

        if (CollectionHelper.isFixedEmptyMap(extProps)) {
            extProps = new HashMap<>();
        }
        extProps.put(name, value);
    }

    public void removeExtProp(String name) {
        checkAllowChange();
        extProps.remove(name);
    }

    protected void checkAllowChange() {

    }

    @Override
    public Set<String> prop_names() {
        return extProps.keySet();
    }

    @Override
    public boolean prop_has(String name) {
        return hasExtProp(name);
    }

    @Override
    public void prop_set(String name, Object value) {
        setExtProp(name, value);
    }

    @Override
    public void prop_remove(String name) {
        removeExtProp(name);
    }

    @Override
    public Object prop_get(String propName) {
        Object value = getExtProp(propName);
        if (value == null && !isAllowedExtProp(propName)) {
            throw new NopException(ERR_REFLECT_UNKNOWN_PROP).param(ARG_OBJ, this).param(ARG_PROP, propName);
        }
        return value;
    }
}