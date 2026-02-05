package io.nop.report.core.engine.expand;

import io.nop.report.core.engine.IXptRuntime;
import io.nop.report.core.model.ExpandedCell;

import java.util.Deque;

public class CellNullExpander implements ICellExpander {
    public static final CellNullExpander INSTANCE = new CellNullExpander();

    @Override
    public void expand(ExpandedCell cell, Deque<ExpandedCell> processing, IXptRuntime xptRt) {
    }
}
