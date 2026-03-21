/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.functions.sink;

import io.nop.stream.core.common.functions.SinkFunction;

/**
 * 两阶段提交 Sink 函数接口，用于实现 Exactly-Once 语义。
 * 
 * <p>工作流程：
 * <ol>
 *   <li>beginTransaction() - 开始事务</li>
 *   <li>invoke() - 在事务中写入数据</li>
 *   <li>preCommit() - checkpoint 完成前预提交</li>
 *   <li>commit() - checkpoint 完成后提交</li>
 *   <li>rollback() - 失败时回滚</li>
 * </ol>
 *
 * @param <IN> 输入数据类型
 */
public interface TwoPhaseCommitSinkFunction<IN> extends SinkFunction<IN> {

    /**
     * 开始一个新事务。
     *
     * @throws Exception 开始事务失败
     */
    void beginTransaction() throws Exception;

    /**
     * 在当前事务中写入数据。
     *
     * @param value 要写入的数据
     * @throws Exception 写入失败
     */
    @Override
    default void consume(IN value) throws Exception {
        invoke(value);
    }

    /**
     * 在当前事务中写入数据。
     *
     * @param value 要写入的数据
     * @throws Exception 写入失败
     */
    void invoke(IN value) throws Exception;

    /**
     * 预提交当前事务（checkpoint 完成前）。
     *
     * @param checkpointId 关联的 checkpoint ID
     * @throws Exception 预提交失败
     */
    void preCommit(long checkpointId) throws Exception;

    /**
     * 提交事务（checkpoint 完成后）。
     *
     * @param checkpointId 关联的 checkpoint ID
     * @throws Exception 提交失败
     */
    void commit(long checkpointId) throws Exception;

    /**
     * 回滚当前事务。
     *
     * @throws Exception 回滚失败
     */
    void rollback() throws Exception;

    /**
     * 恢复事务（从 checkpoint 恢复时调用）。
     *
     * @param checkpointId 要恢复的 checkpoint ID
     * @throws Exception 恢复失败
     */
    default void recover(long checkpointId) throws Exception {
        // 默认实现：回滚后开始新事务
        rollback();
        beginTransaction();
    }
}
