package io.nop.xlang.compare;

import io.nop.core.lang.eval.IExecutableExpression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个 corpus 单元的矩阵化执行报告：列结果 + 缺席列记录 + 整体判定。
 * skipped 不计入通过；整体 PASS 要求无 FAIL 且至少一列 PASS。
 */
public final class CompareUnitReport {
    private final CompareUnit unit;
    private final IExecutableExpression compiledTree;
    private final List<ColumnOutcome> columnOutcomes;
    private final List<ColumnSkipRecord> skipRecords;

    public CompareUnitReport(CompareUnit unit, IExecutableExpression compiledTree,
                             List<ColumnOutcome> columnOutcomes, List<ColumnSkipRecord> skipRecords) {
        this.unit = unit;
        this.compiledTree = compiledTree;
        this.columnOutcomes = Collections.unmodifiableList(new ArrayList<>(columnOutcomes));
        this.skipRecords = Collections.unmodifiableList(new ArrayList<>(skipRecords));
    }

    public CompareUnit getUnit() {
        return unit;
    }

    public IExecutableExpression getCompiledTree() {
        return compiledTree;
    }

    public List<ColumnOutcome> getColumnOutcomes() {
        return columnOutcomes;
    }

    public List<ColumnSkipRecord> getSkipRecords() {
        return skipRecords;
    }

    public boolean isPassed() {
        if (columnOutcomes.isEmpty())
            return false;
        for (ColumnOutcome outcome : columnOutcomes) {
            if (!outcome.isPassed())
                return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return unit.getName() + " => " + (isPassed() ? "PASS" : "FAIL")
                + " columns=" + columnOutcomes + " skips=" + skipRecords;
    }
}
