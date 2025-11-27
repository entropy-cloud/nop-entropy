package io.nop.core.lang.xml.json;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.CDataText;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.CoreConstants;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.utils.SourceLocationHelper;

import java.util.Collection;
import java.util.Map;

public class XJsonHelper {
    public static Object decodeJsonValue(SourceLocation loc, Object value) {
        if (value instanceof String || value instanceof CDataText) {
            String text = value.toString();
            if (text.startsWith(CoreConstants.ATTR_EXPR_PREFIX)) {
                text = text.substring(CoreConstants.ATTR_EXPR_PREFIX.length());
                loc = SourceLocationHelper.offset(loc, CoreConstants.ATTR_EXPR_PREFIX.length());
                return JsonTool.parseNonStrict(loc, text);
            }
        }
        return value;
    }

    public static ValueWithLocation decodeJsonValueLoc(ValueWithLocation vl) {
        Object value = vl.getValue();
        if (value instanceof String || value instanceof CDataText) {
            String text = value.toString();
            if (text.startsWith(CoreConstants.ATTR_EXPR_PREFIX)) {
                text = text.substring(CoreConstants.ATTR_EXPR_PREFIX.length());
                SourceLocation loc = SourceLocationHelper.offset(vl.getLocation(),
                        CoreConstants.ATTR_EXPR_PREFIX.length());
                Object v = JsonTool.parseNonStrict(loc, text);
                return ValueWithLocation.of(loc, v);
            }
        }
        return vl;
    }

    public static ValueWithLocation encodeJsonValueLoc(ValueWithLocation vl) {
        if (needAddJsonPrefix(vl.getValue())) {
            String str = "@:" + JsonTool.stringify(vl.getValue());
            return ValueWithLocation.of(vl.getLocation(), str);
        }
        return vl;
    }

    static boolean needAddJsonPrefix(Object value) {
        if (value == null)
            return false;

        if (value instanceof Number || value instanceof Boolean || value instanceof Collection || value instanceof Map)
            return true;

        return false;
    }
}
