package io.nop.metadata.dao.dto;

import io.nop.api.core.annotations.data.DataBean;

/**
 * 数据源凭证绑定/解绑结果（审计 MD-3：替代 Map&lt;String,Object&gt; 返回；字段名与原 Map 键一致，GraphQL 面不变）。
 */
@DataBean
public class CredentialBindResultDTO {
    private String dataSourceId;
    private String credentialId;

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }
}
