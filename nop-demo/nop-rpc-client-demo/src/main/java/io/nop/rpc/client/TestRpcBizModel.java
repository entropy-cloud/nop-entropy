/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.client;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.core.context.IServiceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;

@BizModel("TestRpc")
public class TestRpcBizModel {
    static final Logger LOG = LoggerFactory.getLogger(TestRpcBizModel.class);

    @Inject
    EchoService echoService;

    @Inject
    TestRpc rpc;

    /**
     * 调用Spring实现的REST服务
     */
    @BizQuery
    public String test(@Name("myArg") String myArg) {
        return echoService.echo(myArg, "aa");
    }

    @BizMutation
    public MyResponse myMethod(@RequestBean MyRequest req, FieldSelectionBean selection, IServiceContext ctx) {
        String value1 = "type=" + req.getType() + ",name=" + req.getName();
        String value2 = value1 + "|2";
        if (selection != null) {
            value1 = "selection=" + selection + ":" + value1;
            if(selection.hasField("value2"))
                value2 = "selection=" + selection + ":" + echoService.echo(req.getName(), "bb");
        }

        // ctx实现了ICancelToken接口，通过它可以判断服务调用是否已经被取消，也可以监听取消事件
        if (ctx.isCancelled()) {
            LOG.info("nop.rpc.invocation-cancelled:{}", ctx.getCancelReason());
        }

        ctx.appendOnCancel((cancelReason) -> {
            LOG.info("nop.rpc.invocation-cancelled:{}", cancelReason);
        });

        MyResponse res = new MyResponse();
        res.setValue1(value1);
        res.setValue2(value2);
        return res;
    }

    /**
     * 调用Nop平台实现的服务（实际会调用到上面的myMethod函数）
     */
    @BizMutation
    public MyResponse invokeRpc(@RequestBean MyRequest req, FieldSelectionBean selection, IServiceContext ctx) {
        ApiRequest<MyRequest> request = ApiRequest.build(req);
        request.setSelection(selection);
        // 如果取消了当前RPC调用，则实际也会取消对于myMethod的调用，这里将cancelToken传递给了myMethod
        return rpc.myMethod(request, ctx).get();
    }
}