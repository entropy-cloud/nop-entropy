package io.nop.table.validator.validate;

import io.nop.api.core.util.IVariableScope;

public class StatVariableScope implements IVariableScope {
    private final ColumnStats stats;

    public StatVariableScope(ColumnStats stats) {
        this.stats = stats;
    }

    @Override
    public Object getValue(String name) {
        return getValueByPropPath(name);
    }

    @Override
    public Object getValueByPropPath(String name) {
        StatProperty prop = StatProperty.fromName(name);
        return prop != null ? prop.getValue(stats) : null;
    }
}
