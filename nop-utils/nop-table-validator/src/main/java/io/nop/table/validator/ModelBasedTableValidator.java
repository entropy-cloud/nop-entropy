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
            // 行级校验的字段引用（如 condition 中的 age）从 scope 解析，必须把行数据放入 scope
            Map<String, Object> rowVars = new HashMap<>();
            for (int colIdx = 0; colIdx < columnNames.length; colIdx++) {
                rowVars.putIfAbsent(columnNames[colIdx], rowAdaptor.getValue(row, colIdx));
            }
            rowVars.put("rowIndex", rowIdx);

            IVariableScope rowScope;
            IEvalScope scope = context != null ? context.getEvalScope() : null;
            if (scope != null) {
                // 子作用域中的行数据覆盖同名上下文变量，同时保留外部上下文变量的可见性
                rowScope = scope.newChildScope(rowVars);
            } else {
                rowScope = new BeanVariableScope(rowVars);
            }
            for (ModelBasedValidator rv : rowValidators) {
                rv.validate(rowScope, new RowWiseCollector(collector, rowIdx));
            }
        }

        for (int colIdx = 0; colIdx < columnNames.length; colIdx++) {
            Object value = rowAdaptor.getValue(row, colIdx);
            statsArr[colIdx].accumulate(value);
        }
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
            // ModelBasedValidator 可能传入 Collections.emptyMap()（errorParams 为空时），
            // 不能直接 put，先复制为可变 Map 再补充 rowIndex
            Map<String, Object> params = error.getParams() == null
                    ? new HashMap<>() : new HashMap<>(error.getParams());
            params.put("rowIndex", rowIndex);
            error.setParams(params);
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
