/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta;

public class ObjMetaHelper {
    public static String getDisplayName(ISchema schema, String propPath) {
        if (schema == null)
            return propPath;

        IObjPropMeta propMeta = schema.getProp(propPath);
        if (propMeta != null) {
            return propMeta.getDisplayName();
        }

        int pos = propPath.indexOf('.');
        if (pos < 0)
            return null;

        String varName = propPath.substring(0, pos);
        propMeta = schema.getProp(varName);
        if (propMeta == null)
            return null;

        String subName = propPath.substring(pos + 1);
        String subDisplayName = getDisplayName(propMeta.getSchema(), subName);
        if (subDisplayName == null)
            return null;

        String displayName = propMeta.getDisplayName();
        if (displayName == null)
            displayName = propMeta.getName();
        return displayName + '.' + subDisplayName;
    }
}
