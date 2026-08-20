/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.backend;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;

/**
 * 加载期绑定降级产物（I10，设计 java §五"解释器兜底"分支的执行体形态）：包装编译单元原树与
 * 加载期裁定的降级原因，随模型缓存复用；执行直通解释器（原树经全局执行器）。
 *
 * <p>降级观测已在加载期完成（生产 binder 按 D5 分级自记，或不可用条目由绑定助手记）——
 * 本类型执行时不再产生观测、不再咨询 binder、不重算指纹（避免 stale/租户稳态场景每次执行的
 * 重复开销与噪声）。资源变更检测驱动的重载会重新裁定。
 */
public class EvalStaticDegradedExecutable implements IExecutableExpression {

    private final IExecutableExpression sourceTree;

    private final String reason;

    public EvalStaticDegradedExecutable(IExecutableExpression sourceTree, String reason) {
        this.sourceTree = sourceTree;
        this.reason = reason;
    }

    public IExecutableExpression getSourceTree() {
        return sourceTree;
    }

    /** 加载期裁定的降级原因（分级观测已在加载期记录） */
    public String getReason() {
        return reason;
    }

    @Override
    public SourceLocation getLocation() {
        return sourceTree.getLocation();
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        return EvalExprProvider.getGlobalExecutor().execute(sourceTree, rt);
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("[degraded:").append(reason).append(']');
        sourceTree.display(sb);
    }

    @Override
    public boolean allowBreakPoint() {
        return sourceTree.allowBreakPoint();
    }

    @Override
    public boolean containsReturnStatement() {
        return sourceTree.containsReturnStatement();
    }

    @Override
    public boolean containsBreakStatement() {
        return sourceTree.containsBreakStatement();
    }

    @Override
    public void visit(IExecutableExpressionVisitor visitor) {
        sourceTree.visit(visitor);
    }
}
