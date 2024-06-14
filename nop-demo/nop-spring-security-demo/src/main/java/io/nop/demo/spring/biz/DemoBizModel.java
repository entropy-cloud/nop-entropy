package io.nop.demo.spring.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;

@BizModel("Demo")
public class DemoBizModel {
    @BizQuery
    @Auth(permissions = "Demo:hello")
    public String hello(@Name("message") String message) {
        return "hello:" + message;
    }
}
