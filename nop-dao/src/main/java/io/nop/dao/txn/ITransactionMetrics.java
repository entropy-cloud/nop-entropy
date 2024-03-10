/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.txn;

public interface ITransactionMetrics {
    void onTransactionOpen(String txnGroup);

    void onTransactionSuccess(String txnGroup);

    void onTransactionFailure(String txnGroup);
}
