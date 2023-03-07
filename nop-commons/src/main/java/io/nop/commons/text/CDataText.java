/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.text;

import io.nop.api.core.json.IJsonString;
import io.nop.commons.util.StringHelper;

import java.io.Serializable;

public class CDataText implements Serializable, IJsonString {
    private static final long serialVersionUID = 5689941180001528397L;
    private final String text;

    public CDataText(String text) {
        this.text = text == null ? "" : text;
    }

    public String toString() {
        return text;
    }

    public String getText() {
        return text;
    }

    public int hashCode() {
        return text.hashCode();
    }

    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof CDataText))
            return false;

        CDataText other = (CDataText) o;
        return text.equals(other.text);
    }

    public static Object encodeIfNecessary(Object value) {
        if (value instanceof String) {
            String str = value.toString();
            if (StringHelper.xmlValueNeedEscape(str))
                return new CDataText(str);
        }
        return value;
    }
}