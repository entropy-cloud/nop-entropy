package io.nop.batch.exp.config;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.exp.config._gen._ImportTableConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static io.nop.batch.exp.DbToolExpErrors.ARG_FIELD_NAME;
import static io.nop.batch.exp.DbToolExpErrors.ARG_FIELD_NAMES;
import static io.nop.batch.exp.DbToolExpErrors.ARG_TABLE_NAME;
import static io.nop.batch.exp.DbToolExpErrors.ERR_EXP_UNKNOWN_KEY_FIELD;

public class ImportTableConfig extends _ImportTableConfig {
    public ImportTableConfig() {

    }

    @Override
    public ImportTableConfig cloneInstance() {
        ImportTableConfig ret = super.cloneInstance();
        if (ret.getFields() != null) {
            ret.setFields(ret.getFields().stream().map(TableFieldConfig::cloneInstance).collect(Collectors.toList()));
        }
        return ret;
    }

    public String getSourceTableName() {
        String from = getFrom();
        if (from == null)
            from = getName();
        return from;
    }

    public List<String> getTargetFieldNames() {
        List<String> ret = new ArrayList<>(getFields().size());
        for (TableFieldConfig field : getFields()) {
            if (field.isIgnore())
                continue;
            ret.add(field.getName());
        }
        return ret;
    }

    public List<String> getSourceFieldNames() {
        List<String> ret = new ArrayList<>(getFields().size());
        for (TableFieldConfig field : getFields()) {
            if (field.isIgnore())
                continue;
            ret.add(field.getSourceFieldName());
        }
        return ret;
    }

    public List<TableFieldConfig> getKeyFieldConfigs() {
        List<TableFieldConfig> ret = new ArrayList<>();
        List<String> keyFields = getKeyFields();
        if (keyFields == null)
            return ret;

        for (String keyField : keyFields) {
            TableFieldConfig fieldConfig = getField(keyField);
            if (fieldConfig == null)
                throw new NopException(ERR_EXP_UNKNOWN_KEY_FIELD)
                        .param(ARG_FIELD_NAME, keyField)
                        .param(ARG_TABLE_NAME, getName())
                        .param(ARG_FIELD_NAMES, keySet_fields());
            ret.add(fieldConfig);
        }
        return ret;
    }
}
