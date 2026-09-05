/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.report.core.coordinate;

import io.nop.excel.model.XptCellModel;
import io.nop.excel.model.constants.XptExpandType;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedTable;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 列层次坐标解析缺陷回归测试（与行版本行为对齐）。
 * <p>
 * 模板结构：G 为列展开根格，P1/P2 为其展开子格（同名P），
 * X1/X2 分别为 P1/P2 的子格（同名X），E1/E2 为无子格的命名格。
 */
public class TestColLayerCoordinateResolve {

    private final ExpandedTable table = new ExpandedTable(4, 4);

    private ExpandedCell cell(int row, int col, String name, XptExpandType expandType) {
        ExpandedCell cell = table.getCell(row, col);
        XptCellModel model = new XptCellModel();
        model.setName(name);
        model.setExpandType(expandType);
        cell.setModel(model);
        table.addNamedCell(cell);
        return cell;
    }

    private ExpandedCell resolve(ExpandedCell from, String cellName, CellCoordinate coord) {
        CellLayerCoordinate layerCoord = new CellLayerCoordinate();
        layerCoord.setCellName(cellName);
        layerCoord.setColCoordinates(Arrays.asList(coord));
        List<ExpandedCell> result = CellCoordinateHelper.resolveLayerCoordinate(from, layerCoord);
        return result == null || result.isEmpty() ? null : result.get(0);
    }

    private ExpandedCell setup() {
        ExpandedCell g = cell(0, 0, "G", XptExpandType.c);
        ExpandedCell p1 = cell(0, 1, "P", XptExpandType.c);
        ExpandedCell p2 = cell(0, 2, "P", XptExpandType.c);
        ExpandedCell x1 = cell(1, 1, "X", null);
        ExpandedCell x2 = cell(1, 2, "X", null);
        cell(1, 3, "E", XptExpandType.c);
        cell(2, 3, "E", XptExpandType.c);

        p1.setColParent(g);
        p2.setColParent(g);
        g.addColChild(p1);
        g.addColChild(p2);

        x1.setColParent(p1);
        x2.setColParent(p2);
        p1.addColChild(x1);
        p2.addColChild(x2);
        return p1;
    }

    @Test
    public void testResolveColParentChainBySelfName() {
        // 从P1出发解析 X[...;P:2]：应命中第二个P展开格下的X2
        ExpandedCell p1 = setup();
        CellCoordinate coord = new CellCoordinate();
        coord.setCellName("P");
        coord.setPosition(2);
        assertSame(table.getCell(1, 2), resolve(p1, "X", coord));
    }

    @Test
    public void testResolveColCoordinatePosEqualsSize() {
        // 从P1出发解析 P[...;G:1]：G下有两个P实例，位置1（1-based）应有效
        ExpandedCell p1 = setup();
        CellCoordinate coord = new CellCoordinate();
        coord.setCellName("G");
        coord.setPosition(1);
        List<ExpandedCell> result = CellCoordinateHelper.resolveLayerCoordinate(p1, layerCoord("P", coord));
        assertEquals(2, result.size());
    }

    @Test
    public void testResolveColCoordinateWithoutPositionMatchesRowSemantics() {
        // 不带位置的坐标与行版本语义一致：无法定位时返回null而不是数组越界
        ExpandedCell p1 = setup();
        CellCoordinate coord = new CellCoordinate();
        coord.setCellName("G");
        assertNull(resolve(p1, "P", coord));
    }

    @Test
    public void testResolveToCellWithoutColDescendantsReturnsNull() {
        // 坐标命中的单元格没有列子格时，应返回null而不是NPE
        ExpandedCell p1 = setup();
        CellCoordinate coord = new CellCoordinate();
        coord.setCellName("E");
        coord.setPosition(1);
        assertNull(resolve(p1, "X", coord));
    }

    private CellLayerCoordinate layerCoord(String cellName, CellCoordinate coord) {
        CellLayerCoordinate layerCoord = new CellLayerCoordinate();
        layerCoord.setCellName(cellName);
        layerCoord.setColCoordinates(Arrays.asList(coord));
        return layerCoord;
    }
}
