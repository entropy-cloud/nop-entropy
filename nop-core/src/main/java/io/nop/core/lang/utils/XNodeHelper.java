package io.nop.core.lang.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.XNode;

import static io.nop.core.CoreErrors.ARG_ALLOWED_NS;
import static io.nop.core.CoreErrors.ARG_EXPR;
import static io.nop.core.CoreErrors.ARG_XML_NAME;
import static io.nop.core.CoreErrors.ERR_XML_NOT_ALLOW_COMPILE_PHASE_EXPR;
import static io.nop.core.CoreErrors.ERR_XML_NOT_ALLOW_CUSTOM_NAMESPACE;
import static io.nop.core.CoreErrors.ERR_XML_NOT_ALLOW_EXPR;

public class XNodeHelper {
    /**
     * 检查是否是可以安全被编译的xpl标签。
     * 1. 只允许使用指定名字空间中的自定义标签。
     * 2. 不允许使用编译期表达式和xpl名字空间
     * 3. 可以选择是否允许EL表达式。
     *
     * @param node      需要被检查的节点
     * @param allowedNs 允许的自定义名字空间
     * @param allowExpr 是否允许EL表达式
     */
    public static void checkSafeXpl(XNode node, String allowedNs, boolean allowExpr) {
        node.forEachNode(n -> {
            checkNs(n.getLocation(), n.getTagName(), allowedNs);
            checkExpr(n.content(), allowExpr);
            n.forEachAttr((name, vl) -> {
                checkNs(vl.getLocation(), n.getTagName(), allowedNs);
                checkExpr(vl, allowExpr);
            });
        });
    }

    private static void checkNs(SourceLocation loc, String xmlName, String allowedNs) {
        int pos = xmlName.indexOf(':');
        if (pos > 0 && !StringHelper.startsWithNamespace(xmlName, allowedNs)) {
            throw new NopException(ERR_XML_NOT_ALLOW_CUSTOM_NAMESPACE)
                    .loc(loc).param(ARG_XML_NAME, xmlName).param(ARG_ALLOWED_NS, allowedNs);
        }
    }

    private static void checkExpr(ValueWithLocation vl, boolean allowExpr) {
        if (vl.isEmpty())
            return;

        if (vl.isStringValue()) {
            String str = vl.asString();
            if (str.indexOf("#{") >= 0 && str.indexOf('}') > 0)
                throw new NopException(ERR_XML_NOT_ALLOW_COMPILE_PHASE_EXPR)
                        .source(vl).param(ARG_EXPR, str);

            if (!allowExpr) {
                if (str.indexOf("${") >= 0 && str.indexOf('}') > 0)
                    throw new NopException(ERR_XML_NOT_ALLOW_EXPR)
                            .source(vl).param(ARG_EXPR, str);
            }
        }
    }
}