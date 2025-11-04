package io.nop.xlang.xmeta.utils;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.xlang.xmeta.IObjPropMeta;
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

    public static Object getPropValue(Object entity, IObjPropMeta propMeta, IEvalContext context) {
        if (propMeta.getGetter() != null) {
            IEvalFunction getter = propMeta.getGetter();
            IEvalScope scope = context.getEvalScope();
            return getter.call3(null, entity, null, propMeta, scope);
        }

        String mapTo = propMeta.getMapToProp();
        if (mapTo != null)
            return BeanTool.getComplexProperty(entity, mapTo);
        return BeanTool.getProperty(entity, propMeta.getName());
    }

    public static void setPropValue(Object entity, Object value, IObjPropMeta propMeta, IEvalContext context) {
        IEvalFunction setter = propMeta.getSetter();
        if (setter != null) {
            setter.call3(null, entity, value, propMeta, context.getEvalScope());
            return;
        }

        if (propMeta.getMapToProp() != null) {
            BeanTool.setComplexProperty(entity, propMeta.getMapToProp(), value);
            return;
        }

        BeanTool.setProperty(entity, propMeta.getName(), value);
    }
}