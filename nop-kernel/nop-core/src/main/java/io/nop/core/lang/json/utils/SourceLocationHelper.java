/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.utils;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.json.JArray;
import io.nop.core.lang.json.JObject;
import io.nop.core.resource.ResourceConstants;

import java.util.Map;

public class SourceLocationHelper {
    public static SourceLocation getPropLocation(Object bean, String propName) {
        if (bean instanceof JObject)
            return ((JObject) bean).getLocation(propName);
        if (bean instanceof ISourceLocationGetter)
            return ((ISourceLocationGetter) bean).getLocation();
        return null;
    }

    public static SourceLocation getElementLocation(Object bean, int index) {
        if (bean instanceof JArray)
            return ((JArray) bean).getLocation(index);
        if (bean instanceof ISourceLocationGetter)
            return ((ISourceLocationGetter) bean).getLocation();
        return null;
    }

    public static SourceLocation getBeanLocation(Object bean) {
        if (bean instanceof ISourceLocationGetter)
            return ((ISourceLocationGetter) bean).getLocation();
        return null;
    }

    public static SourceLocation offset(SourceLocation loc, int colOffset) {
        if (loc == null)
            return null;
        return loc.offset(0, colOffset);
    }

    public static SourceLocation getLocation(Map<String, Object> o, String name) {
        if (o instanceof JObject)
            return ((JObject) o).getLocation(name);
        return null;
    }

    public static String toAbsolutePath(SourceLocation loc, String path) {
        if(loc == null)
            return path;

        if (path.equals(CoreConstants.RESOURCE_NS_SUPER)) {
            return ResourceConstants.RESOURCE_NS_SUPER + ':' + loc.getPath();
        }
        return StringHelper.absolutePath(loc.getPath(), path);
    }
}
