package io.nop.dbtool.exp.config;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dbtool.exp.config._gen._ExportTableConfig;
import io.nop.orm.dao.DaoQueryHelper;

public class ExportTableConfig extends _ExportTableConfig {
    public ExportTableConfig() {

    }

    public SQL buildSQL() {
        SQL.SqlBuilder sb = SQL.begin().name(getName());
        if (!StringHelper.isEmpty(getSql())) {
            sb.append(getSql());
        } else {
            sb.append("select * from ").append(getName());

            if (getFilter() != null) {
                sb.where();
                DaoQueryHelper.appendFilter(sb, null, getFilter());
            }
        }
        return sb.end();
    }
}
