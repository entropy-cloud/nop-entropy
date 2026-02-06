package io.nop.orm.filter;

import io.nop.api.core.auth.IDataAuthChecker;
import io.nop.api.core.beans.ITreeBean;
import io.nop.commons.text.marker.IMarkedString;
import io.nop.commons.text.marker.MarkedString;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.SyntaxMarker;
import io.nop.orm.sql.IEntityFilterProvider;
import io.nop.orm.sql.ISqlCompileTool;
import jakarta.inject.Inject;

import static io.nop.orm.OrmConstants.DATA_AUTH_ACTION_SQL;

public class DataAuthEntityFilterProvider implements IEntityFilterProvider {
    private IDataAuthChecker dataAuthChecker;

    @Inject
    public void setDataAuthChecker(IDataAuthChecker dataAuthChecker) {
        this.dataAuthChecker = dataAuthChecker;
    }

    @Override
    public IMarkedString getEntityFilter(SyntaxMarker marker, SQL sql,
                                         ISqlCompileTool compiler) {
        IServiceContext ctx = IServiceContext.getCtx();
        if (ctx == null)
            ctx = new ServiceContextImpl();

        String bizObj = StringHelper.simpleClassName(marker.getEntityName());

        ITreeBean filter = dataAuthChecker.getFilter(bizObj, DATA_AUTH_ACTION_SQL, ctx);
        IMarkedString filteredSql = FilterSqlHelper.buildFilterSQL(filter, marker.getEntityName(),
                marker.getAlias(), compiler, ctx);
        return filteredSql == null ? MarkedString.EMPTY : filteredSql;
    }
}
