/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.api.mfa;

import io.nop.api.core.auth.IUserContext;

import java.util.Map;

/**
 * 操作级 MFA 拦截判定 SPI（设计 §3.1/§3.3）。
 * <p>
 * executor 检查点（RPC 单操作路径与 GraphQL 文档路径，{@code GraphQLActionAuthChecker.check}
 * 之后）对声明了 {@link MfaRequired} 的 field selection 调用本接口；判定语义按设计 §3.3
 * 伪代码（总开关 → 未启用 MFA 用户不拦截 → 票核验（scene/operation/sessionId/窗口 + 原子
 * consume）→ 创建 challenge → 抛 {@code ERR_AUTH_OPERATION_MFA_REQUIRED}）。
 * <p>
 * 接线载体：{@code GraphQLEngine} 可选注入（{@code @Inject @Nullable} setter）+
 * {@code IGraphQLExecutionContext} 透出——nop-auth-service 提供实现 bean；未注册实现时
 * 等价于操作级 MFA 关闭，框架零介入。接口仅依赖 nop-api-core 类型（nop-biz-auth-api
 * 不依赖 nop-core），userContext 为 executor 侧服务上下文的用户上下文（可能为 null，
 * 实现方负责兜底）。
 */
public interface IOperationMfaChecker {

    /**
     * 对单个声明了 {@link MfaRequired} 的 operation 执行拦截判定。
     *
     * @param operationName  GraphQL operation 全名（{@code bizObjName__action}，与
     *                       {@code ReflectionBizModelBuilder} 的 operation 命名口径一致，
     *                       同时是票 payload.operation 的比对值）
     * @param userContext    当前请求的用户上下文（可能为 null——构建期已禁止 publicAccess
     *                       组合，null 仅为兜底，实现方放行或拒绝自行裁定）
     * @param requestHeaders 当前请求头（票经 {@code X-Nop-Op-Mfa-Token} 头传递，可能为 null）
     */
    void check(String operationName, IUserContext userContext, Map<String, Object> requestHeaders);
}
