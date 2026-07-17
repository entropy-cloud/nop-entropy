package io.nop.table.validator;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.util.IVariableScope;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.model.query.BeanVariableScope;
import io.nop.core.model.query.FilterBeanEvaluator;
import io.nop.core.model.validator.ModelBasedValidator;
import io.nop.table.validator.compile.TableValidatorCompiled;
import io.nop.table.validator.compile.TableValidatorCompiler;
import io.nop.table.validator.model.TableValidatorModel;
import io.nop.table.validator.validate.ColumnStats;
import io.nop.table.validator.validate.StatVariableScope;

import java.util.*;

public class ModelBasedTableValidator<T> implements ITableValidator<T> {
    private final TableValidatorCompiled compiled;
    private final IRowDataAdaptor<T> rowAdaptor;
    private final FilterBeanEvaluator evaluator;
    private ColumnStats[] statsArr;
    private String[] columnNames;
    private int totalRowCount;
    private IValidationErrorCollector collector;

    public ModelBasedTableValidator(TableValidatorModel model, IRowDataAdaptor<T> rowAdaptor) {
        this.compiled = new TableValidatorCompiler().compile(model);
        this.rowAdaptor = rowAdaptor;
        this.evaluator = FilterBeanEvaluator.INSTANCE;
    }

    public ModelBasedTableValidator(TableValidatorCompiled compiled, IRowDataAdaptor<T> rowAdaptor) {
        this.compiled = compiled;
        this.rowAdaptor = rowAdaptor;
        this.evaluator = FilterBeanEvaluator.INSTANCE;
    }

    public TableValidatorCompiled getCompiled() {
        return compiled;
    }

    @Override
    public String getValidatorName() {
        String desc = compiled.getDescription();
        return desc != null ? desc : "table-validator";
    }

    @Override
    public void beginTable(String[] columnNames, IValidationErrorCollector collector) {
        this.columnNames = columnNames;
        this.collector = collector;
        this.totalRowCount = 0;
        this.statsArr = new ColumnStats[columnNames.length];
        for (int i = 0; i < columnNames.length; i++) {
            statsArr[i] = new ColumnStats();
        }
    }

    @Override
    public void validateRow(T row, IEvalContext context) {
        int rowIdx = totalRowCount++;

        ModelBasedValidator[] rowValidators = compiled.getRowValidators();
        if (rowValidators != null) {
            IEvalScope scope = context != null ? context.getEvalScope() : null;
            if (scope != null) {
                scope.setLocalValue("rowIndex", rowIdx);
            }
            IVariableScope rowScope = scope != null ? scope : new BeanVariableScope(Map.of("rowIndex", rowIdx));
            for (ModelBasedValidator rv : rowValidators) {
                rv.validate(rowScope, new RowWiseCollector(collector, rowIdx));
            }
        }

        for (int colIdx = 0; colIdx < columnNames.length; colIdx++) {
            Object value = rowAdaptor.getValue(row, colIdx);
            statsArr[colIdx].accumulate(value);
        }
    }

    private IVariableScope buildRowScope(int rowIdx) {
        Map<String, Object> map = new HashMap<>();
        map.put("rowIndex", rowIdx);
        return new BeanVariableScope(map);
    }

    @Override
    public void endTable() {
        validateStatChecks();
        validateTableChecks();
        this.statsArr = null;
        this.columnNames = null;
        this.collector = null;
    }

    private void validateStatChecks() {
        TableValidatorCompiled.CompiledStatCheck[] checks = compiled.getStatChecks();
        if (checks == null)
            return;

        Map<String, Integer> colIndex = new HashMap<>();
        for (int i = 0; i < columnNames.length; i++) {
            colIndex.put(columnNames[i], i);
        }

        for (TableValidatorCompiled.CompiledStatCheck check : checks) {
            Integer colIdx = colIndex.get(check.columnName);
            if (colIdx == null)
                continue;

            ColumnStats stats = statsArr[colIdx];
            if (stats.getCount() == 0)
                continue;

            if (check.filter != null) {
                IVariableScope scope = new StatVariableScope(stats);
                if (Boolean.TRUE.equals(evaluator.visitRoot(check.filter, scope)))
                    continue;
            }

            ErrorBean error = collector.buildError(check.errorCode);
            error.setSeverity(check.severity);
            error.setDescription(check.errorDescription);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("column", check.columnName);
            if (check.errorParams != null)
                params.putAll(check.errorParams);
            error.setParams(params);
            collector.addError(error);
        }
    }

    private void validateTableChecks() {
        TableValidatorCompiled.CompiledTableCheck[] checks = compiled.getTableChecks();
        if (checks == null)
            return;

        for (TableValidatorCompiled.CompiledTableCheck check : checks) {
            if (check.condition != null) {
                Map<String, Object> scopeMap = new HashMap<>();
                scopeMap.put("rowCount", (double) totalRowCount);
                scopeMap.put("columnCount", (double) columnNames.length);
                if (!Boolean.TRUE.equals(evaluator.visitRoot(check.condition,
                        new BeanVariableScope(scopeMap)))) {
                    continue;
                }
            }

            boolean ok = true;
            if (check.rowCountMin != null && totalRowCount < check.rowCountMin)
                ok = false;
            if (check.rowCountMax != null && totalRowCount > check.rowCountMax)
                ok = false;
            if (check.columnCountMin != null && columnNames.length < check.columnCountMin)
                ok = false;
            if (check.columnCountMax != null && columnNames.length > check.columnCountMax)
                ok = false;

            if (!ok) {
                ErrorBean error = collector.buildError(check.errorCode);
                error.setSeverity(check.severity);
                error.setDescription(check.errorDescription);
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("rowCount", totalRowCount);
                params.put("columnCount", columnNames.length);
                if (check.errorParams != null)
                    params.putAll(check.errorParams);
                error.setParams(params);
                collector.addError(error);
            }
        }
    }

    static class RowWiseCollector implements IValidationErrorCollector {
        private final IValidationErrorCollector delegate;
        private final int rowIndex;

        RowWiseCollector(IValidationErrorCollector delegate, int rowIndex) {
            this.delegate = delegate;
            this.rowIndex = rowIndex;
        }

        @Override
        public void addError(ErrorBean error) {
            if (error.getParams() == null)
                error.setParams(new HashMap<>());
            error.getParams().put("rowIndex", rowIndex);
            delegate.addError(error);
        }

        @Override
        public ErrorBean buildError(String errorCode) {
            return delegate.buildError(errorCode);
        }

        @Override
        public void addException(Throwable e) {
            delegate.addException(e);
        }
    }
}
