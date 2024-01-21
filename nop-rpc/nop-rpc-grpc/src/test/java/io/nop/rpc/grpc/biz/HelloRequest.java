package io.nop.rpc.grpc.biz;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;

@DataBean
public class HelloRequest {
    private String name;

    @PropMeta(propId = 1)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
