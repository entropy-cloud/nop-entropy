package io.nop.biz.impl;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.biz.api.IBizObject;
import io.nop.biz.crud.IQueryTransformer;
import io.nop.core.context.IServiceContext;

public class EmptyQueryTransformer implements IQueryTransformer {

    @Override
    public void transform(QueryBean filter, String authObjName,
                          String action, IBizObject bizObj, IServiceContext context) {

    }
}
