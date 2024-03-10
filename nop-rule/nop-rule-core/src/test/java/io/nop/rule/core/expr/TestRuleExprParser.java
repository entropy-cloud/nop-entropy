/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.core.expr;

import io.nop.api.core.beans.TreeBean;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.expr.filter.ExpressionToFilterBeanTransformer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestRuleExprParser {

    @Test
    public void testCompare() {
        String expr = " >= 3 and < 5";

        checkExpr("(obj?.myVar >= 3) && (obj?.myVar < 5)", expr);
    }

    void checkExpr(String expected, String expr) {
        Expression exprObj = new RuleExprParser("obj.myVar").parseExpr(null, expr);
        assertEquals(expected, exprObj.toExprString());
    }

    @Test
    public void testToFilter() {
        String expr = " >= 3 and < 5 and ==2";
        Expression exprObj = new RuleExprParser("obj.myVar").parseExpr(null, expr);
        TreeBean tree = new ExpressionToFilterBeanTransformer().transform(exprObj);
        assertEquals("and", tree.getTagName());
        XNode node = XNode.fromTreeBean(tree);
        node.addJsonPrefix();
        node.dump();

        assertEquals("<and><ge name=\"obj.myVar\" value=\"@:3\"/><lt name=\"obj.myVar\" value=\"@:5\"/><eq name=\"obj.myVar\" value=\"@:2\"/></and>",
                node.outerXml(false, false));
    }
}
