/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class ExtensibleBean implements Serializable {

    private static final long serialVersionUID = -7378385925174172682L;

    private Map<String, Object> attrs;

    public Map<String, Object> getAttrs() {
        return attrs;
    }

    public void setAttrs(Map<String, Object> attrs) {
        this.attrs = attrs;
    }

    public Object getAttr(String name) {
        if (attrs == null) {
            return null;
        }
        return attrs.get(name);
    }

    public void setAttr(String name, Object value) {
        if (attrs == null) {
            attrs = new LinkedHashMap<>();
        }
        attrs.put(name, value);
    }

    public void removeAttr(String name) {
        if (attrs != null)
            attrs.remove(name);
    }
}
