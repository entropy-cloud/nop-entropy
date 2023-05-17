package io.nop.rpc.client;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class MyResponse {
    String value1;
    String value2;

    public String getValue1() {
        return value1;
    }

    public void setValue1(String value1) {
        this.value1 = value1;
    }

    public String getValue2() {
        return value2;
    }

    public void setValue2(String value2) {
        this.value2 = value2;
    }
}
