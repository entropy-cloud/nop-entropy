/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.tcc.api;

import io.nop.api.core.beans.ApiRequest;

import java.io.Serializable;

public class TccBranchRequest implements Serializable {
    private String txnGroup;
    private String parentBranchId;
    private int parentBranchNo;
    private String serviceName;
    private String serviceMethod;
    private ApiRequest<?> request;
    private String confirmMethod;
    private String cancelMethod;

    public String getTxnGroup() {
        return txnGroup;
    }

    public void setTxnGroup(String txnGroup) {
        this.txnGroup = txnGroup;
    }

    public int getParentBranchNo() {
        return parentBranchNo;
    }

    public void setParentBranchNo(int parentBranchNo) {
        this.parentBranchNo = parentBranchNo;
    }

    public String getParentBranchId() {
        return parentBranchId;
    }

    public void setParentBranchId(String parentBranchId) {
        this.parentBranchId = parentBranchId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceMethod() {
        return serviceMethod;
    }

    public void setServiceMethod(String serviceMethod) {
        this.serviceMethod = serviceMethod;
    }

    public ApiRequest<?> getRequest() {
        return request;
    }

    public void setRequest(ApiRequest<?> request) {
        this.request = request;
    }

    public String getConfirmMethod() {
        return confirmMethod;
    }

    public void setConfirmMethod(String confirmMethod) {
        this.confirmMethod = confirmMethod;
    }

    public String getCancelMethod() {
        return cancelMethod;
    }

    public void setCancelMethod(String cancelMethod) {
        this.cancelMethod = cancelMethod;
    }
}