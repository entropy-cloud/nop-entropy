/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.component;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;

public class ResourceVersionHelper {
    /**
     * 格式为v123这种形式，前缀+整数
     */
    public static boolean isNumberVersionString(String str) {
        if (StringHelper.isEmpty(str))
            return false;

        if (!str.startsWith("v"))
            return false;

        if (str.length() > 10)
            return false;

        for (int i = 1, n = str.length(); i < n; i++) {
            if (!StringHelper.isDigit(str.charAt(i)))
                return false;
        }
        return true;
    }

    public static boolean endsWithNumberVersion(String str) {
        String last = StringHelper.lastPart(str, '/');
        return isNumberVersionString(last);
    }

    public static String getModelName(String str) {
        int pos = str.lastIndexOf('/');
        int pos2 = str.lastIndexOf('/', pos - 1);
        return str.substring(pos2 + 1, pos);
    }

    public static long getNumberVersion(String str) {
        int pos = str.lastIndexOf('/');
        if (pos < 0) {
            pos = 0;
        } else {
            pos++;
        }
        if (str.charAt(pos) != 'v')
            throw new IllegalArgumentException("nop.err.core.invalid-version-string:" + str);
        pos++;
        int pos2 = str.indexOf('.', pos);
        if (pos2 < 0)
            pos2 = str.length();
        return ConvertHelper.toLong(str.substring(pos, pos2));
    }

    /**
     * 版本号大的排在前面
     */
    public static int compareWithNumberVersion(String str1, String str2) {
        long v1 = getNumberVersion(str1);
        long v2 = getNumberVersion(str2);
        return Long.compare(v2, v1);
    }

    public static int compareResourceVersion(IResource res1, IResource res2) {
        return compareWithNumberVersion(res1.getPath(), res2.getPath());
    }

    public static boolean isVersionFile(IResource resource, String fileType) {
        String name = resource.getName();
        if (!name.startsWith("v"))
            return false;
        if (!name.endsWith(fileType))
            return false;
        if (name.charAt(name.length() - fileType.length()) != '.')
            return false;
        return true;
    }

    public static String buildPath(String basePath, String name, Long version, String fileType) {
        return StringHelper.appendPath(basePath, name) + "/v" + version + "." + fileType;
    }
}
