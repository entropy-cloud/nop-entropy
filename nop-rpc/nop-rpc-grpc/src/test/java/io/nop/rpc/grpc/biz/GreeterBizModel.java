package io.nop.rpc.grpc.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.RequestBean;

@BizModel("Greeter")
public class GreeterBizModel {
    @BizMutation
    public void check() {
        System.out.println("check");
    }

    @BizQuery
    public HelloReply SayHello(@RequestBean HelloRequest request) {
        HelloReply reply = new HelloReply();
        reply.setName(request.getName() + "-result");
        return reply;
    }
}
