/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.backend;

/**
 * 动态翻译后端契约（truffle 后端形态）：运行时对动态编译产生的 Executable 树执行翻译与求值。
 *
 * <p>单元级翻译失败（第三分支）契约：实现方消费后端自身的翻译失败观测事件接口，按 sourceKey
 * 关联本次求值——命中即返回 {@link EvalBackendDynamicOutcome#fallback(String, Object)}
 * （裁决入口改走解释器并记降级观测）；未命中的异常是真实求值错误，必须原样重抛（fail-fast，
 * 禁止静默吞没）。
 */
public interface IEvalDynamicBackend extends IEvalExecutionBackend {

    /**
     * 执行动态路径求值：翻译（或命中翻译缓存）并执行求值。
     *
     * @param request 求值请求（树 + 求值现场 + resourcePath 提示）
     * @return 求值结果（值 + 身份证据 artifact）或单元级降级 fallback 标记
     */
    EvalBackendDynamicOutcome executeDynamic(EvalBackendDynamicRequest request);
}
