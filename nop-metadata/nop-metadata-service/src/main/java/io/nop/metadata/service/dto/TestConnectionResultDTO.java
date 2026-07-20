package io.nop.metadata.service.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

/**
 * 测试数据源连通性结果 DTO（来源：{@code NopMetaDataSourceBizModel.testConnection}）。
 */
@DataBean
public class TestConnectionResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean connected;
    private String databaseProductName;
    private String error;

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public String getDatabaseProductName() {
        return databaseProductName;
    }

    public void setDatabaseProductName(String databaseProductName) {
        this.databaseProductName = databaseProductName;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
