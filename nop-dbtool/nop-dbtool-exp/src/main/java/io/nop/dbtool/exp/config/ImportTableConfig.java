package io.nop.dbtool.exp.config;

import io.nop.dbtool.exp.config._gen._ImportTableConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ImportTableConfig extends _ImportTableConfig {
    public ImportTableConfig() {

    }

    @Override
    public ImportTableConfig cloneInstance() {
        ImportTableConfig ret = super.cloneInstance();
        if (ret.getFields() != null) {
            ret.setFields(ret.getFields().stream().map(ImportTableFieldConfig::cloneInstance).collect(Collectors.toList()));
        }
        return ret;
    }

    public String getSourceName() {
        String from = getFrom();
        if (from == null)
            from = getName();
        return from;
    }

    public List<String> getTargetFieldNames() {
        List<String> ret = new ArrayList<>(getFields().size());
        for (ImportTableFieldConfig field : getFields()) {
            if (field.isIgnore())
                continue;
            ret.add(field.getName());
        }
        return ret;
    }

    public List<String> getSourceFieldNames() {
        List<String> ret = new ArrayList<>(getFields().size());
        for (ImportTableFieldConfig field : getFields()) {
            if (field.isIgnore())
                continue;
            ret.add(field.getSourceFieldName());
        }
        return ret;
    }
}
