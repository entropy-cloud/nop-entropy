/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
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
