/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl;

import io.nop.api.core.annotations.data.ImmutableBean;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.xlang.XLangConstants;

import java.io.Serializable;

@ImmutableBean
public class XplTagName implements Serializable {
    private final String namespace;
    private final String name;

    public XplTagName(String namespace, String name) {
        this.namespace = StringHelper.isEmpty(namespace) ? XLangConstants.XPL_DEFAULT_NS : namespace;
        this.name = Guard.notEmpty(name, "xpl tag name is empty");
    }

    public static XplTagName parse(String tagName) {
        int pos = tagName.indexOf(':');
        if (pos < 0)
            return new XplTagName(null, tagName);
        return new XplTagName(tagName.substring(0, pos), tagName.substring(pos + 1));
    }

    public String toString() {
        if (XLangConstants.XPL_DEFAULT_NS.equals(namespace))
            return name;
        return namespace + ':' + name;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }
}