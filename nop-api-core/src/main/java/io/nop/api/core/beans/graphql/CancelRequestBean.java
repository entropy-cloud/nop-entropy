package io.nop.api.core.beans.graphql;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class CancelRequestBean {
    private String reqId;
    private Object data;

    public String getReqId() {
        return reqId;
    }

    public void setReqId(String reqId) {
        this.reqId = reqId;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
