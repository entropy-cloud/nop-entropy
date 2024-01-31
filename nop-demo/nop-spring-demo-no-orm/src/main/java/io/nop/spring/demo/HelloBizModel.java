package io.nop.spring.demo;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;

@BizModel("Hello")
public class HelloBizModel {

    @BizQuery
    public String hello(@Name("message") String message) {
        return "Hi, " + message;
    }
}
