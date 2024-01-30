package io.nop.demo.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.NopException;

import static io.nop.demo.DemoErrors.ARG_NAME;
import static io.nop.demo.DemoErrors.ERR_DEMO_NOT_FOUND;

@BizModel("Demo")
public class DemoBizModel {
    @BizQuery
    public String hello(@Name("message") String message) {
        return "Hi," + message;
    }

    @BizMutation
    public DemoResponse testOk(@RequestBean DemoRequest request) {
        DemoResponse ret = new DemoResponse();
        ret.setName(request.getName());
        ret.setResult("ok");
        return ret;
    }

    @BizMutation
    public DemoResponse testError(@RequestBean DemoRequest request) {
        throw new NopException(ERR_DEMO_NOT_FOUND).param(ARG_NAME, request.getName());
    }
}
