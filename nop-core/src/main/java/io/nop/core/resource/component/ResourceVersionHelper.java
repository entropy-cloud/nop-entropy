package io.nop.core.resource.component;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;

public class ResourceVersionHelper {
    /**
     * 格式为v123这种形式，前缀+整数
     */
    public static boolean isIntegerVersionString(String str) {
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

    public static boolean endsWithIntegerVersion(String str) {
        String last = StringHelper.lastPart(str, '/');
        return isIntegerVersionString(last);
    }

    public static int getIntegerVersion(String str) {
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
        return ConvertHelper.toInt(str.substring(pos, pos2));
    }

    /**
     * 版本号大的排在前面
     */
    public static int compareWithIntegerVersion(String str1, String str2) {
        int v1 = getIntegerVersion(str1);
        int v2 = getIntegerVersion(str2);
        return Integer.compare(v2, v1);
    }

    public static int compareResourceVersion(IResource res1, IResource res2) {
        return compareWithIntegerVersion(res1.getPath(), res2.getPath());
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
}
