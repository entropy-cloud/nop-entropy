/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.core.interceptor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.util.ApiHeaders;

/**
 * 保存在IContext中随着上下文传递的TCC事务相关信息
 */
public class TccContext {
    private String txnGroup;
    private String txnId;
    private String branchId;
    private int branchNo;

    public static TccContext buildFromRequest(ApiRequest<?> request) {
        String txnId = ApiHeaders.getTxnId(request);
        if (txnId == null)
            return null;

        String txnGroup = ApiHeaders.getTxnGroup(request);
        String branchId = ApiHeaders.getTxnBranchId(request);
        int branchNo = ApiHeaders.getTxnBranchNo(request, 0);

        TccContext tccContext = new TccContext();
        tccContext.setTxnGroup(txnGroup);
        tccContext.setBranchId(branchId);
        tccContext.setBranchNo(branchNo);
        tccContext.setTxnId(txnId);
        return tccContext;
    }

    /**
     * TccContext存放在系统内置的IContext中
     */
    public static TccContext getCurrent() {
        IContext context = ContextProvider.currentContext();
        if (context == null)
            return null;
        return (TccContext) context.getAttribute(TccContext.class.getName());
    }

    public static IContext setCurrent(TccContext tccContext) {
        IContext context = ContextProvider.getOrCreateContext();
        context.setAttribute(TccContext.class.getName(), tccContext);
        return context;
    }

    public static IContext removeCurrent(TccContext tccContext) {
        IContext context = ContextProvider.currentContext();
        if(context != null) {
            context.removeAttribute(TccContext.class.getName(), tccContext);
        }
        return context;
    }

    public String getTxnGroup() {
        return txnGroup;
    }

    public void setTxnGroup(String txnGroup) {
        this.txnGroup = txnGroup;
    }

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
    }

    public String getBranchId() {
        return branchId;
    }

    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }

    public int getBranchNo() {
        return branchNo;
    }

    public void setBranchNo(int branchNo) {
        this.branchNo = branchNo;
    }
}