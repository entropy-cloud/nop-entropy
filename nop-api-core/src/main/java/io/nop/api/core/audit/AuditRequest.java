/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.audit;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ExtensibleBean;

import java.sql.Timestamp;

@DataBean
public class AuditRequest extends ExtensibleBean {
    private String bizObj;
    private String action;
    private String entityId;

    private String message;

    // 请求数据
    private String requestData;

    // 结果数据
    private String responseData;

    private String errorCode;
    private int resultStatus;

    private String userName;
    private String clientId;
    private String appId;

    private Timestamp actionTime;
    private String sessionId;
    private long usedTime;

    private String tenantId;

    public static AuditRequest newRequest(String bizObj, String action, String entityId, IUserContext userContext) {
        AuditRequest req = new AuditRequest();
        req.setBizObj(bizObj);
        req.setAction(action);
        req.setEntityId(entityId);
        req.setUserName(userContext.getUserName());
        req.setSessionId(userContext.getSessionId());
        req.setTenantId(userContext.getTenantId());
        return req;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public long getUsedTime() {
        return usedTime;
    }

    public void setUsedTime(long usedTime) {
        this.usedTime = usedTime;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getBizObj() {
        return bizObj;
    }

    public void setBizObj(String bizObj) {
        this.bizObj = bizObj;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getRequestData() {
        return requestData;
    }

    public void setRequestData(String requestData) {
        this.requestData = requestData;
    }

    public String getResponseData() {
        return responseData;
    }

    public void setResponseData(String responseData) {
        this.responseData = responseData;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public int getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(int resultStatus) {
        this.resultStatus = resultStatus;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public Timestamp getActionTime() {
        return actionTime;
    }

    public void setActionTime(Timestamp actionTime) {
        this.actionTime = actionTime;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}