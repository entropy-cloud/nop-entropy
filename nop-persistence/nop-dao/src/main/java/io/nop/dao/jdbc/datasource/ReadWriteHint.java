/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.datasource;

import java.util.function.Supplier;

/**
 * 读写分离（plan 2319）的显式路由提示，ThreadLocal三态原语：{@link Hint#PRIMARY}（强制主库）、
 * {@link Hint#REPLICA}（强制从库）、{@link Hint#UNSET}（未设置，由路由DataSource按
 * 事务/只读意图判定）。与{@link DynamicDataSource#runWith}、
 * {@link io.nop.dao.api.QuerySpaceEnv#runWithQuerySpace}同族的ThreadLocal作用域原语：
 * 必须通过runWith形式使用，任务结束后在finally中恢复原值。
 *
 * <p>语义（设计文档 ai-dev/design/nop-dao-readwrite-splitting-design.md §3.1）：
 * 显式hint优先级最高，覆盖"活动事务主库、只读语句从库"的缺省判定。
 * {@code forceReplica}且从库不可用时显式报错，不静默回退主库。</p>
 *
 * <p><b>线程池警告</b>：状态保存在静态ThreadLocal中，线程池复用工作线程时会残留。
 * 严禁手工set/clear跨越请求边界，必须使用runWith*形式确保try/finally恢复，
 * 防止跨请求数据串写（与DynamicDataSource的裁定一致）。</p>
 */
public final class ReadWriteHint {

    public enum Hint {
        /**
         * 强制主库：写后立即读、报表等强一致场景
         */
        PRIMARY,
        /**
         * 强制从库：从库不可用时显式报错，不静默回退主库
         */
        REPLICA,
        /**
         * 未设置：由ReadWriteRoutingDataSource按事务上下文与只读意图判定
         */
        UNSET
    }

    private static final ThreadLocal<Hint> s_hint = new ThreadLocal<>();

    private ReadWriteHint() {
    }

    /**
     * 当前线程生效的hint。无值时返回{@link Hint#UNSET}
     */
    public static Hint currentHint() {
        Hint hint = s_hint.get();
        return hint == null ? Hint.UNSET : hint;
    }

    /**
     * 在PRIMARY作用域内执行任务，任务结束后恢复原hint
     */
    public static <T> T runWithPrimary(Supplier<T> task) {
        return runWith(Hint.PRIMARY, task);
    }

    public static void runWithPrimary(Runnable task) {
        runWith(Hint.PRIMARY, task);
    }

    /**
     * 在REPLICA作用域内执行任务，任务结束后恢复原hint。从库不可用时由路由DataSource显式报错
     */
    public static <T> T runWithReplica(Supplier<T> task) {
        return runWith(Hint.REPLICA, task);
    }

    public static void runWithReplica(Runnable task) {
        runWith(Hint.REPLICA, task);
    }

    /**
     * {@link #runWithReplica(Supplier)}的语义别名：表达"只读意图"传播场景
     * （框架内部由JdbcTemplateImpl桥接调用），与显式forceReplica路由结果相同
     */
    public static <T> T runWithReadOnly(Supplier<T> task) {
        return runWith(Hint.REPLICA, task);
    }

    public static void runWithReadOnly(Runnable task) {
        runWith(Hint.REPLICA, task);
    }

    static <T> T runWith(Hint hint, Supplier<T> task) {
        Hint old = enter(hint);
        try {
            return task.get();
        } finally {
            leave(old);
        }
    }

    static void runWith(Hint hint, Runnable task) {
        Hint old = enter(hint);
        try {
            task.run();
        } finally {
            leave(old);
        }
    }

    static Hint enter(Hint hint) {
        Hint old = currentHint();
        s_hint.set(hint);
        return old;
    }

    static void leave(Hint prevHint) {
        if (prevHint == Hint.UNSET) {
            s_hint.remove();
        } else {
            s_hint.set(prevHint);
        }
    }
}
