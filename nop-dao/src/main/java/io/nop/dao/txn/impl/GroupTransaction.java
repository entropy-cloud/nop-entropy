/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.txn.impl;

public class GroupTransaction extends AbstractTransaction {
    private String txnId;

    public GroupTransaction(String querySpace, String txnId) {
        super(querySpace);
        this.txnId = txnId;
    }

    public String getTxnId() {
        return txnId;
    }

    @Override
    protected void doRollback(Throwable error) {
    }

    @Override
    protected void doCommit() {
    }

    @Override
    protected void doClose() {

    }

    @Override
    protected void doOpen() {

    }
}