package io.nop.dao.api;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.core.context.IEvalContext;

public interface IQueryBuilder {
    QueryBean buildQuery(IEvalContext context);
}
