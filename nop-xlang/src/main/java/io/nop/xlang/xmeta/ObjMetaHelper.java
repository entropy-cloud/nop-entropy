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
