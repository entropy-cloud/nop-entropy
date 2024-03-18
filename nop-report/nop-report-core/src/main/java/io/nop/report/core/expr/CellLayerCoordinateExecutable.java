/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.expr;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.report.core.XptConstants;
import io.nop.report.core.coordinate.CellCoordinateHelper;
import io.nop.report.core.coordinate.CellLayerCoordinate;
import io.nop.report.core.engine.IXptRuntime;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedCellSet;
import io.nop.xlang.exec.AbstractExecutable;

import java.util.List;

import static io.nop.report.core.XptErrors.ERR_XPT_MISSING_VAR_CELL;

public class CellLayerCoordinateExecutable extends AbstractExecutable implements ICellSetExecutable {
    private final CellLayerCoordinate layerCoordinate;
    private final String expr;
    private final boolean absolute;

    public CellLayerCoordinateExecutable(SourceLocation loc, CellLayerCoordinate layerCoordinate, boolean absolute) {
        super(loc);
        this.layerCoordinate = layerCoordinate;
        this.expr = layerCoordinate.toString();
        this.absolute = absolute;
    }

    public CellLayerCoordinate getLayerCoordinate() {
        return layerCoordinate;
    }

    @Override
    public ICellSetExecutable toAbsolute() {
        if (absolute)
            return this;
        return new CellLayerCoordinateExecutable(getLocation(), layerCoordinate, true);
    }

    public String getExpr() {
        return expr;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(layerCoordinate);
    }

    @Override
    public ExpandedCellSet execute(IExpressionExecutor executor, EvalRuntime rt) {
        IXptRuntime xptRt = (IXptRuntime) rt.getValue(XptConstants.VAR_XPT_RT);
        if (xptRt == null)
            throw newError(ERR_XPT_MISSING_VAR_CELL);

        ExpandedCell cell = xptRt.getCell();
        if (cell == null)
            throw newError(ERR_XPT_MISSING_VAR_CELL);

        List<ExpandedCell> cells = absolute ? cell.getTable().getNamedCells(cell.getName()) :
                CellCoordinateHelper.resolveLayerCoordinate(cell, layerCoordinate);
        if (cells == null)
            return null;

        for (ExpandedCell layerCell : cells) {
            xptRt.evaluateCell(layerCell);
        }

        return new ExpandedCellSet(getLocation(), expr, cells);
    }
}
