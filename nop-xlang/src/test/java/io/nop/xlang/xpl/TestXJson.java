/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.XLangOutputMode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestXJson {
    @Test
    public void testNullValue() {
        String xml = "<root><title>${value}</title></root>";
        XNode node = XNodeParser.instance().parseFromText(null, xml);

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, "value", null);

        ExprEvalAction action = newTool().compileTag(node, XLangOutputMode.xml);
        assertEquals("\n<root>\n<title></title></root>", action.generateText(scope));

        action = newTool().compileTag(node, XLangOutputMode.node);
        assertEquals("<_><root><title/></root></_>", action.generateNode(scope).outerXml(false, false));

        node = XNodeParser.instance().forFragments(true).parseFromText(null, xml);
        action = newTool().compileXjson(node);
        Map<String, Object> obj = (Map<String, Object>) action.invoke(scope);
        assertEquals("{\"type\":\"root\",\"title\":null}", JsonTool.serialize(obj, false));
    }

    XLangCompileTool newTool() {
        return XLang.newCompileTool().allowUnregisteredScopeVar(true);
    }
}
