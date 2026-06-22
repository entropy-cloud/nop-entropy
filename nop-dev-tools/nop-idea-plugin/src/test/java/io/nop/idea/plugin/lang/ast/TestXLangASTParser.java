/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.ast;

import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.XLangOutputMode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-31
 */
@RunWith(JUnit4.class)
public class TestXLangASTParser {

    @Test
    public void testParseXlibASTTree() {
        String text = """
                <lib xmlns:x="/nop/schema/xdsl.xdef" xmlns:c="c" xmlns:thisLib="thisLib"
                     x:schema="/nop/schema/xlib.xdef">
                    <tags>
                        <DoSomething>
                            <attr name="disabled" stdDomain="boolean"/>
                            <source>
                                <c:script>
                                    const name = 'Lily';
                                </c:script>
                                <thisLib:Welcome name="${name}" address="my home"/>
                            </source>
                        </DoSomething>
                        <Welcome outputMode="text">
                            <attr name="name" stdDomain="string"/>
                            <attr name="address" stdDomain="string"/>
                            <source>
                                <c:choose>
                                    <when test="${address != null}">
                                        Welcome to ${address}
                                    </when>
                                    <otherwise>
                                        Welcome
                                    </otherwise>
                                </c:choose>
                                , ${name}!
                            </source>
                        </Welcome>
                    </tags>
                </lib>
                """;
    }

    @Test
    public void testParseXplASTTree() {
        XLangCompileTool cp = XLang.newCompileTool();

        XNode node = XNodeParser.instance().forFragments(true).parseFromText(null, """
                <c:script>
                    import io.nop.commons.util.StringHelper;
                    const showMsg = true;
                    const msg = StringHelper.escapeXml('world');
                </c:script>
                <c:if test="${showMsg}">
                    <div>Hello, ${msg}!</div>
                </c:if>
                """);
        Expression expr = cp.parseTagBody(node, XLangOutputMode.node);
        System.out.println(expr.toExprString());
    }
}
