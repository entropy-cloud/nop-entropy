/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.db.migration.core;

import java.util.function.Supplier;

/**
 * 迁移守卫互斥原语 seam（plan 2518 裁定⑧，C1-003）：
 * 串行化 {@link MigrationHistoryManager#ensureHistoryTableExists} 的 DDL 段与按版本键域的
 * {@link MigrationHistoryManager#recordMigration} 复合写段，消除多节点并发 check-then-act。
 *
 * <p>方言矩阵（设计文档 §3.8）：mysql = {@code GET_LOCK/RELEASE_LOCK}（连接级）；
 * postgresql = {@code pg_advisory_lock/pg_advisory_unlock}（session 级，同连接）；
 * H2 及未列方言 = 条件 INSERT 单行守卫表（owner 列比对删除，finally 释放）。
 * WI 1.5/1.6 租约族为对齐选型关系（引用不重建）。</p>
 */
public interface IMigrationLockGuard {

    /**
     * 获取互斥锁（阻塞至超时；超时/失败类型化异常——nop.err.db-migration.guard-lock-timeout，
     * 非静默继续）。
     */
    void acquire(String lockName);

    /**
     * 释放互斥锁（调用方 finally 语义；只释放自己持有的锁）。
     */
    void release(String lockName);

    /**
     * 互斥段包络：acquire → section → finally release。
     */
    default <T> T withLock(String lockName, Supplier<T> section) {
        acquire(lockName);
        try {
            return section.get();
        } finally {
            release(lockName);
        }
    }

    /**
     * Runnable 形态包络（无返回值互斥段）。
     */
    default void withLock(String lockName, Runnable section) {
        acquire(lockName);
        try {
            section.run();
        } finally {
            release(lockName);
        }
    }
}
