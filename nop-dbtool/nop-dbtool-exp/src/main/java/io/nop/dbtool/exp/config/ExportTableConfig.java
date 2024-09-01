package io.nop.dbtool.exp.config;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dbtool.exp.config._gen._ExportTableConfig;
import io.nop.orm.dao.DaoQueryHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ExportTableConfig extends _ExportTableConfig {
    public ExportTableConfig() {

    }

    @Override
    public ExportTableConfig cloneInstance() {
        ExportTableConfig ret = super.cloneInstance();
        if (ret.getFields() != null) {
            ret.setFields(ret.getFields().stream().map(TableFieldConfig::cloneInstance).collect(Collectors.toList()));
        }
        return ret;
    }

    public String getExportFileName(String format) {
        return getName() + "." + format;
    }

    public String getSourceTableName() {
        String from = getFrom();
        if (from == null)
            from = getName();
        return from;
    }

    public SQL buildSQL() {
        SQL.SqlBuilder sb = SQL.begin().name(getName());
        if (!StringHelper.isEmpty(getSql())) {
            sb.append(getSql());
        } else {
            sb.append("select * from ").append(getFrom());

            if (getFilter() != null) {
                sb.where();
                DaoQueryHelper.appendFilter(sb, null, getFilter());
            }
        }
        return sb.end();
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
}
