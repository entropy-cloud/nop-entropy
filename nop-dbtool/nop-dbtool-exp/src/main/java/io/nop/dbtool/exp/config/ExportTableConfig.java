package io.nop.dbtool.exp.config;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.xml.XNode;
import io.nop.dbtool.exp.config._gen._ExportTableConfig;
import io.nop.orm.dao.DaoQueryHelper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    public SQL buildSQL(Integer fetchSize, IEvalContext ctx) {
        if (getSql() != null) {
            SQL sql = getSql().generateSql(ctx);
            if (fetchSize != null) {
                sql = sql.extend().fetchSize(fetchSize).end();
            }
            return sql;
        } else {
            SQL.SqlBuilder sb = SQL.begin().name(getName());
            if (fetchSize != null)
                sb.fetchSize(fetchSize);

            sb.append("select * from ").append(getSourceTableName());

            if (getFilter() != null) {
                XNode node = getFilter().generateNode(ctx);
                if (node != null) {
                    sb.where();
                    DaoQueryHelper.appendFilter(sb, null, node);
                }
            }
            return sb.end();
        }
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

    public Set<String> getSourceFieldNames() {
        Set<String> ret = new LinkedHashSet<>(getFields().size());
        for (TableFieldConfig field : getFields()) {
            if (field.isIgnore())
                continue;
            ret.add(field.getSourceFieldName());
        }
        return ret;
    }
}
