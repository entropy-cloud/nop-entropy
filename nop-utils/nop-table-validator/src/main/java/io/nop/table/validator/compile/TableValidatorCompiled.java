package io.nop.table.validator.compile;

import io.nop.api.core.beans.ITreeBean;
import io.nop.core.model.validator.ModelBasedValidator;

import java.util.Map;

public class TableValidatorCompiled {
    private final String description;
    private final ModelBasedValidator[] rowValidators;
    private final CompiledStatCheck[] statChecks;
    private final CompiledTableCheck[] tableChecks;

    public TableValidatorCompiled(String description,
                                  ModelBasedValidator[] rowValidators,
                                  CompiledStatCheck[] statChecks,
                                  CompiledTableCheck[] tableChecks) {
        this.description = description;
        this.rowValidators = rowValidators;
        this.statChecks = statChecks;
        this.tableChecks = tableChecks;
    }

    public String getDescription() {
        return description;
    }

    public ModelBasedValidator[] getRowValidators() {
        return rowValidators;
    }

    public CompiledStatCheck[] getStatChecks() {
        return statChecks;
    }

    public CompiledTableCheck[] getTableChecks() {
        return tableChecks;
    }

    public static class CompiledStatCheck {
        public final String columnName;
        public final String errorCode;
        public final int severity;
        public final String errorDescription;
        public final Map<String, String> errorParams;
        public final ITreeBean filter;

        public CompiledStatCheck(String columnName, String errorCode, int severity,
                                 String errorDescription, Map<String, String> errorParams,
                                 ITreeBean filter) {
            this.columnName = columnName;
            this.errorCode = errorCode;
            this.severity = severity;
            this.errorDescription = errorDescription;
            this.errorParams = errorParams;
            this.filter = filter;
        }
    }

    public static class CompiledTableCheck {
        public final String errorCode;
        public final int severity;
        public final String errorDescription;
        public final Map<String, String> errorParams;
        public final Integer rowCountMin;
        public final Integer rowCountMax;
        public final Integer columnCountMin;
        public final Integer columnCountMax;
        public final ITreeBean condition;

        public CompiledTableCheck(String errorCode, int severity,
                                  String errorDescription, Map<String, String> errorParams,
                                  Integer rowCountMin, Integer rowCountMax,
                                  Integer columnCountMin, Integer columnCountMax,
                                  ITreeBean condition) {
            this.errorCode = errorCode;
            this.severity = severity;
            this.errorDescription = errorDescription;
            this.errorParams = errorParams;
            this.rowCountMin = rowCountMin;
            this.rowCountMax = rowCountMax;
            this.columnCountMin = columnCountMin;
            this.columnCountMax = columnCountMax;
            this.condition = condition;
        }
    }
}
