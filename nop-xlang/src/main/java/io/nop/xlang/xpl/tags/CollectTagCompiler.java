/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.tags;

import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.CollectOutputExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;

import java.util.List;

import static io.nop.xlang.xpl.XplConstants.OUTPUT_MODE_NAME;
import static io.nop.xlang.xpl.XplConstants.SINGLE_NODE_NAME;
import static io.nop.xlang.xpl.utils.XplParseHelper.checkArgNames;
import static io.nop.xlang.xpl.utils.XplParseHelper.requireAttrEnum;
import static java.util.Arrays.asList;

public class CollectTagCompiler implements IXplTagCompiler {
    public static final CollectTagCompiler INSTANCE = new CollectTagCompiler();

    static final List<String> ATTR_NAMES = asList(OUTPUT_MODE_NAME, SINGLE_NODE_NAME);

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkArgNames(node, ATTR_NAMES);

        XLangOutputMode outputMode = requireAttrEnum(node, OUTPUT_MODE_NAME, XLangOutputMode.class, cp, scope);
        boolean singleNode = node.attrBoolean(SINGLE_NODE_NAME, false);

        XLangOutputMode oldMode = scope.getOutputMode();
        try {
            scope.setOutputMode(outputMode);

            Expression body = cp.parseTagBody(node, scope);
            if (body == null)
                return null;

            return CollectOutputExpression.valueOf(node.getLocation(), outputMode, singleNode, body);
        } finally {
            scope.setOutputMode(oldMode);
        }
    }

}
