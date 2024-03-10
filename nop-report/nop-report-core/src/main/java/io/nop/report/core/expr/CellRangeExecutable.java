/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.expr;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.core.model.table.CellRange;
import io.nop.report.core.XptConstants;
import io.nop.report.core.coordinate.CellCoordinateHelper;
import io.nop.report.core.engine.IXptRuntime;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedCellSet;
import io.nop.xlang.exec.AbstractExecutable;

import java.util.List;

import static io.nop.report.core.XptErrors.ERR_XPT_MISSING_VAR_CELL;

public class CellRangeExecutable extends AbstractExecutable implements ICellSetExecutable {
    private final CellRange cellRange;
    private final String expr;
    private final boolean absolute;

    public CellRangeExecutable(SourceLocation loc, CellRange cellRange, boolean absolute) {
        super(loc);
        this.cellRange = cellRange;
        this.expr = cellRange.toABString();
        this.absolute = absolute;
    }

    public CellRangeExecutable toAbsolute() {
        if (absolute)
            return this;

        return new CellRangeExecutable(getLocation(), cellRange, true);
    }

    public CellRange getCellRange() {
        return cellRange;
    }

    public String getExpr() {
        return expr;
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append(cellRange);
    }

    @Override
    public ExpandedCellSet execute(IExpressionExecutor executor, IEvalScope scope) {
        IXptRuntime xptRt = (IXptRuntime) scope.getValue(XptConstants.VAR_XPT_RT);
        if (xptRt == null)
            throw newError(ERR_XPT_MISSING_VAR_CELL);

        ExpandedCell cell = xptRt.getCell();
        if (cell == null)
            throw newError(ERR_XPT_MISSING_VAR_CELL);

        List<ExpandedCell> cells = absolute ?
                CellCoordinateHelper.resolveAbsoluteCellRange(cell, cellRange) :
                CellCoordinateHelper.resolveCellRange(cell, cellRange);

        for (ExpandedCell layerCell : cells) {
            xptRt.evaluateCell(layerCell);
        }

        return new ExpandedCellSet(getLocation(), expr, cells);
    }
}
