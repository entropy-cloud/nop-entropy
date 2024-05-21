package io.nop.biz.crud;

import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;

public class BizQueryHelper {
    public static void transformFilter(QueryBean query, IObjMeta objMeta, IEvalContext ctx) {
        query.transformFilter(filter -> {
            String name = (String) filter.getAttr(FilterBeanConstants.FILTER_ATTR_NAME);
            if (StringHelper.isEmpty(name)) {
                return filter;
            }
            IObjPropMeta propMeta = objMeta.getProp(name);
            if (propMeta == null)
                return filter;

            IEvalFunction fn = (IEvalFunction) propMeta.prop_get(GraphQLConstants.TAG_GRAPHQL_TRANS_FILTER);
            if (fn == null)
                return filter;

            return fn.call3(null, filter, query, false, ctx.getEvalScope());
        });
    }
}
