package io.nop.dbtool.exp.config;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.dao.DaoQueryHelper;

@DataBean
public class ExportTableConfig {
    private String name;

    private String sql;

    private TreeBean filter;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public TreeBean getFilter() {
        return filter;
    }

    public void setFilter(TreeBean filter) {
        this.filter = filter;
    }

    public SQL buildSQL() {
        SQL.SqlBuilder sb = SQL.begin().name(name);
        if (!StringHelper.isEmpty(sql)) {
            sb.append(sql);
        } else {
            sb.append("select * from ").append(name);

            if (filter != null) {
                sb.where();
                DaoQueryHelper.appendFilter(sb, null, filter);
            }
        }
        return sb.end();
    }
}
