/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.backend;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;

/**
 * 加载期绑定命中产物（I10，设计 java §五"生成类优先"分支的执行体形态）：包装编译单元原树与
 * 生成类绑定，随模型（RCM ComponentCacheEntry.model）缓存复用。
 *
 * <p>已绑定直通语义：统一裁决入口（{@code XLang.execute} choke point）识别本类型后直通执行
 * 绑定体——不重复咨询 binder、不重算树指纹、不产生降级观测；注册表空 fast-path 下经全局执行器
 * 执行时同样直通绑定体（{@link #execute}）。定位信息与结构遍历委托原树（源位置保真、依赖分析
 * 与调试遍历不受包装影响）。
 */
public class EvalStaticBoundExecutable implements IExecutableExpression {

    private final IExecutableExpression sourceTree;

    private final IEvalStaticBinding binding;

    private final String backendId;

    public EvalStaticBoundExecutable(IExecutableExpression sourceTree, IEvalStaticBinding binding, String backendId) {
        this.sourceTree = sourceTree;
        this.binding = binding;
        this.backendId = backendId;
    }

    /** 编译单元的原 Executable 树（指纹校验对象、解释器兜底执行体） */
    public IExecutableExpression getSourceTree() {
        return sourceTree;
    }

    /** 生成类绑定（执行体与身份证据来源） */
    public IEvalStaticBinding getBinding() {
        return binding;
    }

    /** 完成绑定的后端标识（决策记录用） */
    public String getBackendId() {
        return backendId;
    }

    @Override
    public SourceLocation getLocation() {
        return sourceTree.getLocation();
    }

    @Override
    public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
        return binding.execute(rt);
    }

    @Override
    public void display(StringBuilder sb) {
        sb.append("[bound:").append(backendId).append(']');
        sourceTree.display(sb);
    }

    @Override
    public boolean allowBreakPoint() {
        return false;
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
