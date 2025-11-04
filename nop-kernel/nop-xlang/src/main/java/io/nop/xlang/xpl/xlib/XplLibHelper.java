/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl.xlib;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.xpl.IXplTag;
import io.nop.xlang.xpl.IXplTagLib;
import io.nop.xlang.xpl.XplConstants;

import static io.nop.xlang.XLangErrors.ARG_LIB_PATH;
import static io.nop.xlang.XLangErrors.ARG_NS;
import static io.nop.xlang.XLangErrors.ARG_PATH;
import static io.nop.xlang.XLangErrors.ARG_TAG_NAME;
import static io.nop.xlang.XLangErrors.ERR_XPL_INVALID_LIB_FILE_EXT;
import static io.nop.xlang.XLangErrors.ERR_XPL_INVALID_LIB_NAMESPACE;
import static io.nop.xlang.XLangErrors.ERR_XPL_INVALID_LIB_PATH;
import static io.nop.xlang.XLangErrors.ERR_XPL_UNKNOWN_LIB_TAG;

public class XplLibHelper {
    public static void checkLibPath(String libPath) {
        if (libPath == null || !libPath.endsWith(XplConstants.POSTFIX_XLIB))
            throw new NopEvalException(ERR_XPL_INVALID_LIB_PATH).param(ARG_PATH, libPath);
    }

    public static boolean isDefaultNs(String ns) {
        return XplConstants.XPL_DEFAULT_NS.equals(ns) || StringHelper.isEmpty(ns);
    }

    public static String getNamespaceFromTagName(String tagName) {
        int pos = tagName.indexOf(':');
        if (pos < 0)
            return XplConstants.XPL_DEFAULT_NS;
        return tagName.substring(0, pos);
    }

    public static String buildFullTagName(String namespace, String localName) {
        return namespace + ':' + localName;
    }

    public static String getNamespaceFromLibPath(String libPath) {
        if (StringHelper.isEmpty(libPath))
            return XplConstants.XPL_DEFAULT_NS;

        String fileName = StringHelper.fileNameNoExt(libPath);
        int pos3 = fileName.lastIndexOf('!');
        if (pos3 > 0)
            fileName = fileName.substring(0, pos3);
        if (!StringHelper.isValidXmlName(fileName) || fileName.indexOf(':') >= 0)
            throw new NopEvalException(ERR_XPL_INVALID_LIB_NAMESPACE).param(ARG_PATH, libPath).param(ARG_NS, fileName);
        return fileName;
    }

    public static IXplTagLib loadLib(String src) {
        if (!src.endsWith(XLangConstants.FILE_TYPE_XLIB)) {
            throw new NopException(ERR_XPL_INVALID_LIB_FILE_EXT).param(ARG_PATH, src);
        }
        IXplTagLib lib = (IXplTagLib) ResourceComponentManager.instance().loadComponentModel(src);
        return lib;
    }

    public static IXplTag getTag(String libPath, String tagName) {
        return getTag(libPath, tagName, false);
    }

    public static IXplTag getTag(String libPath, String tagName, boolean ignoreUnknown) {
        IXplTagLib lib = loadLib(libPath);
        IXplTag tag = lib.getTag(tagName);
        if (tag == null && !ignoreUnknown)
            throw new NopException(ERR_XPL_UNKNOWN_LIB_TAG).param(ARG_LIB_PATH, libPath).param(ARG_TAG_NAME, tagName);
        return tag;
    }
}
