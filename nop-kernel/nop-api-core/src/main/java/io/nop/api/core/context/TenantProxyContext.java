package io.nop.api.core.context;

public class TenantProxyContext extends DelegateContext {
    private String tenantId;
    private String userId;
    private String userName;

    private String userRefNo;

    public TenantProxyContext(IContext context) {
        super(context);
        this.userId = context.getUserId();
        this.userName = context.getUserName();
        this.userRefNo = context.getUserRefNo();
        this.tenantId = context.getTenantId();
    }

    @Override
    public String getTenantId() {
        return tenantId;
    }

    @Override
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public String getUserRefNo() {
        return userRefNo;
    }

    @Override
    public void setUserRefNo(String userRefNo) {
        this.userRefNo = userRefNo;
    }
}
