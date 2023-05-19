package io.nop.rpc.client;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.FieldSelectionBean;

import javax.inject.Inject;

@BizModel("TestRpc")
public class TestRpcBizModel {

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
    public MyResponse myMethod(@RequestBean MyRequest req, FieldSelectionBean selection) {
        String value1 = "type=" + req.getType() + ",name=" + req.getName();
        String value2 = value1 + "|2";
        if (selection != null) {
            value1 = "selection=" + selection + ":" + value1;
            value2 = "selection=" + selection + ":" + echoService.echo(req.getName(),"bb");
        }
        MyResponse res = new MyResponse();
        res.setValue1(value1);
        res.setValue2(value2);
        return res;
    }

    /**
     * 调用Nop平台实现的服务（实际会调用到上面的myMethod函数）
     */
    @BizMutation
    public MyResponse invokeRpc(@RequestBean MyRequest req, FieldSelectionBean selection) {
        ApiRequest<MyRequest> request = ApiRequest.build(req);
        request.setSelection(selection);
        return rpc.myMethod(request).get();
    }
}