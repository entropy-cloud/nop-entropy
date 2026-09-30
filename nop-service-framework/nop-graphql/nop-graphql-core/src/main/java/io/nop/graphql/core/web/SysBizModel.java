/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.web;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.beans.graphql.CancelRequestBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.graphql.core.engine.IGraphQLEngine;

import jakarta.inject.Inject;

import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_CANCEL_NOT_LOGIN;

/**
 * 平台内置的系统查询。[G5-13-02] cancel为用户可见行为变更：
 * <ul>
 *   <li>要求登录：未登录调用显式失败（ERR_GRAPHQL_CANCEL_NOT_LOGIN）</li>
 *   <li>归属校验：仅允许取消本人发起的在途请求，跨用户取消显式失败
 *       （ERR_GRAPHQL_CANCEL_NOT_OWNER，见{@code CancelTokenManager}）</li>
 *   <li>同一用户携同reqId重试在途请求为幂等重绑定，不会取消先到请求</li>
 * </ul>
 */
@BizModel("Sys")
public class SysBizModel {
    @Inject
    IGraphQLEngine graphQLEngine;

    @BizQuery
    public boolean cancel(@RequestBean CancelRequestBean cancelBean, IServiceContext ctx) {
        if (cancelBean == null)
            return false;
        String id = cancelBean.getReqId();
        if (StringHelper.isEmpty(id))
            return false;
        String userId = ctx.getUserId();
        if (StringHelper.isEmpty(userId))
            throw new NopException(ERR_GRAPHQL_CANCEL_NOT_LOGIN);
        return graphQLEngine.getCancelTokenManager().cancel(id, userId);
    }
}
