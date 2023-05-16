package io.nop.rpc.client;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class MyRequest {
    private String type;
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
