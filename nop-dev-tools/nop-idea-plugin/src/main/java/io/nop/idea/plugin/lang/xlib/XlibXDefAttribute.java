/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.xlib;

import io.nop.commons.type.StdDataType;
import io.nop.xlang.xdef.XDefConstants;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdef.impl.XDefAttribute;
import io.nop.xlang.xdef.parse.XDefTypeDeclParser;
import io.nop.xlang.xpl.xlib.XplTagAttribute;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-23
 */
public class XlibXDefAttribute extends XDefAttribute {
    public final String label;
    public final String desc;

    public XlibXDefAttribute(XplTagAttribute attr) {
        this.label = attr.getDisplayName();
        this.desc = attr.getDescription();

        setName(attr.getName());
        setPropName(attr.getVarName());
        setLocation(attr.getLocation());

        StringBuilder sb = new StringBuilder();
        if (attr.isMandatory()) {
            sb.append(XDefConstants.XDEF_TYPE_PREFIX_MANDATORY);
        }
        if (attr.isInternal() || attr.isDeprecated() || attr.isImplicit()) {
            sb.append(XDefConstants.XDEF_TYPE_PREFIX_DEPRECATED);
        }
        if (attr.getStdDomain() != null) {
            sb.append(attr.getStdDomain());
        } else {
            sb.append(StdDataType.ANY.getName());
        }
        if (attr.getDefaultValue() != null) {
            sb.append('=').append(attr.getDefaultValue());
        }

        XDefTypeDecl type = new XDefTypeDeclParser().parseFromText(attr.getLocation(), sb.toString());
        setType(type);
    }
}
