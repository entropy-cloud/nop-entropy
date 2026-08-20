/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.backend;

import java.util.Set;

/**
 * 执行后端注册 SPI 基础契约（设计 xlang-execution 01 §四）。注册表条目即本契约的实现实例：
 * 条目字段 = 后端标识 + 能力集 + 可用性状态 + 不可用原因。
 *
 * <p>依赖方向（硬纪律）：契约与注册表定义在 nop-xlang；后端模块（nop-xlang-java /
 * nop-xlang-truffle）实现契约并在自身模块初始化代码中显式注册；nop-xlang 不依赖任何后端模块，
 * 对后端实现的感知止步于本契约。注册表不做 classpath 扫描。
 */
public interface IEvalExecutionBackend {

    /** 后端标识（约定值：java / truffle） */
    String getBackendId();

    /** 能力集声明（不允许为空集） */
    Set<EvalBackendCapability> getCapabilities();

    /**
     * 后端是否可用。初始化失败的后端以"不可用条目"注册（返回 false），错误不阻断启动，
     * 保留在 {@link #getUnavailableReason()} 中供诊断查询。
     */
    boolean isAvailable();

    /** 不可用原因；可用时返回 null */
    String getUnavailableReason();
}
