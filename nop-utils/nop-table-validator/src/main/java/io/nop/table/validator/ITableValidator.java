package io.nop.table.validator;

import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.core.context.IEvalContext;

public interface ITableValidator<T> {
    String getValidatorName();

    void beginTable(String[] columnNames, IValidationErrorCollector collector);

    void validateRow(T row, IEvalContext context);

    void endTable();
}
