package io.nop.autotest.core.data;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class SqlCheck {
    private String desc;
    private String sql;
    private Object result;

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }
}
