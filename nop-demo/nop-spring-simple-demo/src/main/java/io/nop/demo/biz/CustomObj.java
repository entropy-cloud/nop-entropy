package io.nop.demo.biz;

import io.nop.api.core.annotations.biz.BizObjName;

@BizObjName("CustomObj")
public class CustomObj {
    private String name;
    private int status;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isActive() {
        return status == 1;
    }
}
