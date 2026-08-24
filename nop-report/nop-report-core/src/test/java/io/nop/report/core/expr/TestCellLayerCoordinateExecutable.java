/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.expr;

import io.nop.core.lang.eval.EvalRuntime;
import io.nop.excel.model.XptCellModel;
import io.nop.report.core.XptConstants;
import io.nop.report.core.coordinate.CellCoordinate;
import io.nop.report.core.coordinate.CellLayerCoordinate;
import io.nop.report.core.engine.IXptRuntime;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedCellSet;
import io.nop.report.core.model.ExpandedTable;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCellLayerCoordinateExecutable {

    @Test
    public void testResolveFailureReturnsEmptySet() {
        ExpandedTable table = new ExpandedTable(1, 1);
        ExpandedCell cell = table.getCell(0, 0);
        XptCellModel model = new XptCellModel();
        model.setName("X");
        cell.setModel(model);

        // 坐标引用不存在的单元格Q，解析结果为空
        CellLayerCoordinate coord = new CellLayerCoordinate();
        coord.setCellName("X");
        CellCoordinate cc = new CellCoordinate();
        cc.setCellName("Q");
        cc.setPosition(1);
        coord.setColCoordinates(Collections.singletonList(cc));

        CellLayerCoordinateExecutable executable = new CellLayerCoordinateExecutable(null, coord, false);

        IXptRuntime xptRt = (IXptRuntime) Proxy.newProxyInstance(IXptRuntime.class.getClassLoader(),
                new Class[]{IXptRuntime.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getCell"))
                        return cell;
                    if (method.getName().equals("hashCode"))
                        return System.identityHashCode(proxy);
                    return null;
                });

        EvalRuntime rt = new EvalRuntime(XLang.newEvalScope());
        rt.getScope().setLocalValue(null, XptConstants.VAR_XPT_RT, xptRt);

        ExpandedCellSet cellSet = executable.execute(null, rt);
        // 解析失败时应返回空集合而不是null，避免下游NPE
        assertNotNull(cellSet);
        assertTrue(cellSet.isEmpty());
    }
}
