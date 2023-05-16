package io.nop.rpc.client;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.ApiRequest;

import javax.inject.Inject;

@BizModel("TestRpc")
public class TestRpcBizModel {

    @Inject
    EchoService echoService;

    @Inject
    TestRpc rpc;

    @BizQuery
    public String test(@Name("myArg") String myArg) {
        return echoService.echo(ApiRequest.build(myArg)).get();
    }

    @BizMutation
    public String myMethod(@RequestBean MyRequest req) {
        return req.getType() + ":" + req.getName();
    }

    @BizMutation
    public String invokeRpc(@RequestBean MyRequest req) {
        return rpc.myMethod(ApiRequest.build(req)).get();
    }
}