/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.annotations.txn;

import java.lang.annotation.Annotation;

public class TransactionalAnnotation implements Transactional {
    private String txnGroup = "";
    private TransactionPropagation propagation = TransactionPropagation.REQUIRED;

    public String txnGroup() {
        return txnGroup;
    }

    public void setTxnGroup(String txnGroup) {
        this.txnGroup = txnGroup;
    }

    public TransactionPropagation propagation() {
        return propagation;
    }

    public void setPropagation(TransactionPropagation propagation) {
        this.propagation = propagation;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return Transactional.class;
    }
}
