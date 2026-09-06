/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.coordinate;

import io.nop.core.lang.json.JsonTool;
import io.nop.excel.model.XptCellModel;
import io.nop.excel.model.constants.XptExpandType;
import io.nop.report.core.expr.ReportExpressionParser;
import io.nop.report.core.model.ExpandedCell;
import io.nop.xlang.ast.Expression;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestCellCoordinate {

    static ExpandedCell newCell(String name, XptExpandType expandType) {
        XptCellModel model = new XptCellModel();
        model.setName(name);
        model.setExpandType(expandType);
        ExpandedCell cell = new ExpandedCell();
        cell.setModel(model);
        return cell;
    }

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

    @Test
    public void testResolveLayerCoordinateFromColParent() {
        // 列展开结构 P -> B(b1,b2) -> X(x1)，从x1按列坐标X[B:1]解析应在列父链上查找
        ExpandedCell p = newCell("P", XptExpandType.c);
        ExpandedCell b1 = newCell("B", XptExpandType.c);
        ExpandedCell b2 = newCell("B", XptExpandType.c);
        ExpandedCell x1 = newCell("X", null);

        b1.setColParent(p);
        b2.setColParent(p);
        x1.setColParent(b1);
        p.addColChild(b1);
        p.addColChild(b2);
        b1.addColChild(x1);

        CellLayerCoordinate coord = new CellLayerCoordinate();
        coord.setCellName("X");
        CellCoordinate cc = new CellCoordinate();
        cc.setCellName("B");
        cc.setPosition(1);
        coord.setColCoordinates(Collections.singletonList(cc));

        List<ExpandedCell> cells = CellCoordinateHelper.resolveLayerCoordinate(x1, coord);
        assertEquals(1, cells.size());
        assertSame(x1, cells.get(0));
    }

    @Test
    public void testResolveColCoordinatesPositionLast() {
        // pos == cells.size() 时应取最后一个元素（与row方向一致）
        ExpandedCell p = newCell("P", XptExpandType.c);
        ExpandedCell b1 = newCell("B", XptExpandType.c);
        ExpandedCell b2 = newCell("B", XptExpandType.c);
        ExpandedCell x1 = newCell("X", null);
        ExpandedCell x2 = newCell("X", null);

        b1.setColParent(p);
        b2.setColParent(p);
        x1.setColParent(b1);
        x2.setColParent(b2);
        p.addColChild(b1);
        p.addColChild(b2);
        b1.addColChild(x1);
        b2.addColChild(x2);

        CellLayerCoordinate coord = new CellLayerCoordinate();
        coord.setCellName("X");
        CellCoordinate cc = new CellCoordinate();
        cc.setCellName("B");
        cc.setPosition(2);
        coord.setColCoordinates(Collections.singletonList(cc));

        List<ExpandedCell> cells = CellCoordinateHelper.resolveLayerCoordinate(x1, coord);
        assertEquals(1, cells.size());
        assertSame(x2, cells.get(0));
    }

    @Test
    public void testResolveColCoordinatesPositionZero() {
        // 显式位置0是非法坐标，应该返回null而不是抛IndexOutOfBoundsException
        ExpandedCell b1 = newCell("B", XptExpandType.c);
        ExpandedCell x1 = newCell("X", null);

        x1.setColParent(b1);
        b1.addColChild(x1);

        CellLayerCoordinate coord = new CellLayerCoordinate();
        coord.setCellName("X");
        CellCoordinate cc = new CellCoordinate();
        cc.setCellName("X");
        cc.setPosition(0);
        coord.setColCoordinates(Collections.singletonList(cc));

        assertNull(CellCoordinateHelper.resolveLayerCoordinate(x1, coord));
    }
}
