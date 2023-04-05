/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.coordinate;

import io.nop.core.lang.json.JsonTool;
import io.nop.report.core.expr.ReportExpressionParser;
import io.nop.xlang.ast.Expression;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestCellCoordinate {

    @Test
    public void testParse() {
        check("C1");
        check("C1[A1:2,B1:1]");
        check("C2[A1:2,B1:2;C1:+3]");
        check("C2[A2:-1]");
        check("C2[A2:!-1,B1]");
    }

    void check(String source) {
        CellLayerCoordinate coord = new ReportExpressionParser().parseLayerCoordinate(null, source);
        assertEquals(source, coord.toString());
    }


    @Test
    public void testBinaryExpr() {
        String source = "A3 + A5 + SUM(B2)";
        Expression expr = new ReportExpressionParser().parseExpr(null, source);
        System.out.println(JsonTool.serialize(expr, true));
    }

    @Test
    public void testRange(){
        String source = "SUM(B2:E4)";
        Expression expr = new ReportExpressionParser().parseExpr(null, source);
        System.out.println(JsonTool.serialize(expr, true));
    }
}
