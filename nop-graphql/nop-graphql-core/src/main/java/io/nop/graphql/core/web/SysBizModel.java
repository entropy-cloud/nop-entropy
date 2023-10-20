/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.web;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.beans.graphql.CancelRequestBean;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.graphql.core.engine.IGraphQLEngine;

import jakarta.inject.Inject;

@BizModel("Sys")
public class SysBizModel {
    @Inject
    IGraphQLEngine graphQLEngine;

    @BizQuery
    public boolean cancel(@RequestBean CancelRequestBean cancelBean, IServiceContext ctx) {
        if (cancelBean == null)
            return false;
        String id = cancelBean.getReqId();
        if (!StringHelper.isEmpty(id))
            return graphQLEngine.cancel(id);
        return false;
    }
}
