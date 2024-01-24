package io.nop.demo.biz;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class DemoResponse {
    private String name;
    private String result;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
