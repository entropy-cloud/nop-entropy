/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.sql_lib;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.ISqlExecutor;
import io.nop.dao.dialect.IDialect;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.OrmConstants;
import io.nop.orm.sql_lib._gen._QuerySqlItemModel;

import java.util.List;

public class QuerySqlItemModel extends _QuerySqlItemModel {
    public QuerySqlItemModel() {

    }

    public QueryBean buildQueryBean(IEvalContext context) {
        XNode node = getSource().generateNode(context);
        return BeanTool.buildBeanFromTreeBean(node, QueryBean.class);
    }

    @Override
    public Object invoke(ISqlExecutor executor, LongRangeBean range, IEvalContext context) {
        IOrmTemplate orm = (IOrmTemplate) executor;

        SqlMethod method = getSqlMethod();
        if (method == null) {
            if (range != null) {
                method = SqlMethod.findPage;
            } else {
                method = SqlMethod.findAll;
            }
        }

        IEvalScope scope = context.getEvalScope();
        IDialect dialect = executor.getDialectForQuerySpace(getQuerySpace());
        ValueWithLocation dialectVl = scope.recordValueLocation(OrmConstants.PARAM_DIALECT);
        ValueWithLocation modelVl = scope.recordValueLocation(OrmConstants.PARAM_SQL_ITEM_MODEL);
        scope.setLocalValue(null, OrmConstants.PARAM_DIALECT, dialect);
        scope.setLocalValue(null, OrmConstants.PARAM_SQL_ITEM_MODEL, this);

        QueryBean query = buildQueryBean(context);

        try {
            switch (method) {
                case findAll: {
                    List<Object> data = processResult(orm.findListByQuery(query, buildRowMapper(executor, getQuerySpace(), scope)), executor);
                    return buildResult(data, scope);
                }
                case findPage: {
                    long offset = range == null ? 0 : range.getOffset();
                    int limit = range == null ? 10 : (int) range.getLimit();
                    query.setOffset(offset);
                    query.setLimit(limit);
                    List<Object> data = orm.findListByQuery(query, buildRowMapper(executor, getQuerySpace(), scope));
                    data = processResult(data, executor);
                    return buildResult(data, scope);
                }
                case findFirst: {
                    Object data = orm.findFirstByQuery(query, buildRowMapper(executor, getQuerySpace(), scope));
                    data = processSingleResult(data, executor);
                    return buildResult(data, scope);
                }
                case exists:
                    return orm.existsByQuery(query);
                default:
                    throw new UnsupportedOperationException();
            }
        } finally {
            scope.restoreValueLocation(OrmConstants.PARAM_DIALECT, dialectVl);
            scope.restoreValueLocation(OrmConstants.PARAM_SQL_ITEM_MODEL, modelVl);
        }
    }

}
