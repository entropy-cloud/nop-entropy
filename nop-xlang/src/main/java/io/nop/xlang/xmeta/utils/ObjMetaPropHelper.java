package io.nop.xlang.xmeta.utils;

import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xpl.IXplTag;
import io.nop.xlang.xpl.IXplTagLib;
import io.nop.xlang.xpl.xlib.XplLibHelper;

public class ObjMetaPropHelper {

    public static IXplTag findTagForDomain(XNode propNode) {
        XNode schemaNode = propNode.childByTag(ObjMetaPropConstants.TAG_SCHEMA);
        if (schemaNode == null)
            return null;

        String domain = schemaNode.attrText(ObjMetaPropConstants.ATTR_DOMAIN);
        String stdDomain = schemaNode.attrText(ObjMetaPropConstants.ATTR_STD_DOMAIN);

        if (domain == null && stdDomain == null)
            return null;

        IXplTagLib tagLib = XplLibHelper.loadLib(ObjMetaPropConstants.LIB_PATH_META_PROP);

        return findTag(tagLib, ObjMetaPropConstants.ATTR_DOMAIN, domain, stdDomain);
    }

    private static IXplTag findTag(IXplTagLib tagLib, String prefix, String domain, String stdDomain) {
        IXplTag tag = null;
        if (domain != null) {
            tag = tagLib.getTag(prefix + '-' + domain);
            if (tag != null)
                return tag;
        }

        if (stdDomain != null)
            tag = tagLib.getTag(prefix + '-' + stdDomain);
        return tag;
    }
}