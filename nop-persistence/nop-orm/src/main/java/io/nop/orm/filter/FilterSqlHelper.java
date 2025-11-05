package io.nop.orm.filter;

import io.nop.api.core.beans.ITreeBean;
import io.nop.commons.text.marker.MarkedString;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.sql.FilterBeanToSQLTransformer;
import io.nop.core.lang.sql.SQL;
import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.sql.ISqlCompileTool;

import java.util.List;

public class FilterSqlHelper {
    public static SQL.SqlBuilder buildFilterSQL(ITreeBean filter, String entityName, String alias,
                                                ISqlCompileTool tool, IServiceContext ctx) {
        if (filter == null)
            return null;

        if (alias == null)
            alias = "o";

        SQL.SqlBuilder sb = SQL.begin();
        sb.select().field(alias, "id");
        sb.from().append(entityName).as(alias);
        sb.where();

        new FilterBeanToSQLTransformer(sb, true, alias).visit(filter, ctx.getEvalScope());

        ICompiledSql compiledSql = tool.compileSql("filter", sb.getText(), true, null,
                true, false, false);

        List<Object> params = compiledSql.buildParams(sb.getMarkerValues());

        sb = compiledSql.getSql().extend().changeMarkerValues(params);

        String sqlText = compiledSql.getSql().getText();

        int pos = sqlText.indexOf("where ");
        if (compiledSql.getReadEntityNames().size() > 1) {
            // 多个表
            String prefix = " and " + tool.getIdText(entityName, alias) + " in(";
            sb.insertAt(0, new MarkedString(prefix));
            sb.append(')');
        } else {
            sb.removeRange(0, pos + "where ".length());
            sb.insertAt(0, new MarkedString(" and "));
        }
        return sb;
    }
}
