/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.txn;

import io.nop.api.core.util.IOrdered;

public interface ITransactionListener extends IOrdered, Comparable<ITransactionListener> {
    enum CompleteStatus {
        COMMIT, ROLLBACK,
        /**
         * 在commit或者rollback过程中抛出异常将导致unknown
         */
        UNKNOWN
    }

    default int compareTo(ITransactionListener o) {
        return Integer.compare(order(), o.order());
    }

    /**
     * 在准备提交的时候执行。在两阶段提交协议中，在prepare成功之后调用。beforeCompletion在beforeCommit之后执行
     */
    default void onBeforeCommit(ITransaction txn) {
    }

    /**
     * 成功提交之后执行
     */
    default void onAfterCommit(ITransaction txn) {
    }

    /**
     * 在commit或者rollback之前执行。在beforeCommit之后执行，即使beforeCommit抛出异常，也会执行到这里
     */
    default void onBeforeCompletion(ITransaction txn) {

    }

    /**
     * 在调用commit/rollback操作之后回调
     *
     * @param status 如果commit或者rollback失败，则状态是unknown
     */
    default void onAfterCompletion(ITransaction txn, CompleteStatus status, Throwable exception) {
    }

    default void onOpen(ITransaction txn) {

    }

    default void onClose(ITransaction txn) {
    }
}