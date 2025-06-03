/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.component.version;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;

import static io.nop.core.CoreErrors.ARG_BASE_PATH;
import static io.nop.core.CoreErrors.ARG_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_INVALID_VERSIONED_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_VERSIONED_PATH_NO_VERSION;

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

//    public static String getModelName(String str) {
//        String basePath;
//        if (str.startsWith("/nop/wf/")) {
//            basePath = "/nop/wf/";
//        } else if (str.startsWith("wf:")) {
//            basePath = "wf:";
//        } else if (str.startsWith("resolve-wf:")) {
//            basePath = "resolve-wf:";
//        }
//        int pos = str.lastIndexOf('/');
//        int pos2 = str.lastIndexOf('/', pos - 1);
//        return str.substring(pos2 + 1, pos);
//    }

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
        return isVersionFileName(name, fileType);
    }

    public static boolean isVersionFileName(String name, String fileType) {
        if (!name.startsWith("v"))
            return false;
        if (!name.endsWith(fileType))
            return false;
        if (name.charAt(name.length() - fileType.length() - 1) != '.')
            return false;
        return true;
    }

    public static String buildResolvePath(String modelType, String name, Long version) {
        StringBuilder sb = new StringBuilder();
        sb.append("resolve-").append(modelType).append(':').append(name);
        if (version != null && version > 0) {
            sb.append("/v").append(version);
        }
        return sb.toString();
    }

    public static String buildPath(String basePath, String name, Long version, String fileType) {
        if (version == null || version <= 0)
            version = 1L;
        return StringHelper.appendPath(basePath, name) + "/v" + version + "." + fileType;
    }

    public static VersionedName parseVersionedName(String path, String basePath) {
        if (!path.startsWith(basePath) || path.length() < basePath.length() + 2)
            throw new NopException(ERR_RESOURCE_INVALID_VERSIONED_PATH)
                    .param(ARG_PATH, path)
                    .param(ARG_BASE_PATH, path);

        String subPath = path.substring(basePath.length());
        if (subPath.startsWith("/"))
            subPath = subPath.substring(1);

        int pos = subPath.lastIndexOf('/');
        if (pos < 0) {
            String name = subPath;
            return new VersionedName(name, -1L);
        }

        String name = subPath.substring(0, pos);

        String versionStr = subPath.substring(pos + 1);
        int pos2 = versionStr.indexOf('.');
        if (pos2 > 0)
            versionStr = versionStr.substring(0, pos2);

        if (!isNumberVersionString(versionStr))
            throw new NopException(ERR_RESOURCE_INVALID_VERSIONED_PATH)
                    .param(ARG_PATH, path)
                    .param(ARG_BASE_PATH, basePath);

        return new VersionedName(name, getNumberVersion(versionStr));
    }

    public static VersionedName parseVersionedName(String path, String basePath, boolean requireVersion) {
        if (!requireVersion)
            return parseVersionedName(path, basePath);

        VersionedName versionedName = parseVersionedName(path, basePath);
        if (versionedName.getVersion() <= 0)
            throw new NopException(ERR_RESOURCE_VERSIONED_PATH_NO_VERSION)
                    .param(ARG_PATH, path);
        return versionedName;
    }
}
