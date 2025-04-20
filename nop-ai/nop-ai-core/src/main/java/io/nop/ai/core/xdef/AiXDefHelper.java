package io.nop.ai.core.xdef;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdsl.XDslParseHelper;

import java.util.Iterator;
import java.util.Map;

public class AiXDefHelper {
    public static XNode transformForAi(XNode defNode) {
        ValueWithLocation xdefValue = defNode.attrValueLoc("xdef:value");
        if (!xdefValue.isNull()) {
            defNode.setContentValue(transformDefType(xdefValue.getLocation(), "xdef:value", xdefValue.asString()));
        } else {
            ValueWithLocation content = defNode.content();
            if (!content.isNull())
                defNode.setContentValue(transformDefType(content.getLocation(), "body", content.asString()));
        }

        Iterator<Map.Entry<String, ValueWithLocation>> it = defNode.attrValueLocs().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ValueWithLocation> entry = it.next();
            String name = entry.getKey();
            if (name.startsWith("xdef:") || name.startsWith("x:") || name.startsWith("xmlns:") || name.equals("xmlns")) {
                it.remove();
            } else {
                ValueWithLocation vl = entry.getValue();
                String defType = transformDefType(vl.getLocation(), name, vl.asString());
                entry.setValue(ValueWithLocation.of(vl.getLocation(), defType));
            }
        }

        for (XNode child : defNode.getChildren()) {
            transformForAi(child);
        }
        return defNode;
    }

    static String transformDefType(SourceLocation loc, String propName, String defType) {
        XDefTypeDecl typeDecl = XDslParseHelper.parseDefType(loc, propName, defType);
        return typeDecl.getStdDomain();
    }
}
