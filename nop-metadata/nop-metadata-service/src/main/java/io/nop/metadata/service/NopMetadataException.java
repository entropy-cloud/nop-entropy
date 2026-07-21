/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

/**
 * nop-metadata 模块级异常类（plan 2026-07-19-1250-3 Phase 2 维度09-05）。
 *
 * <p>用于模块内部 Executor/Helper 抛出未携带 ErrorCode 的异常场景（替代 IllegalArgumentException /
 * UnsupportedOperationException / 裸 RuntimeException）。模块对外公共 API 仍优先使用
 * {@code new NopException(ErrorCode)} + {@link NopMetadataErrors} 常量。
 *
 * <p>提供四个构造器：
 * <ul>
 *   <li>{@link #NopMetadataException(String)} — 仅消息（内部 helper 用；将 String 包成 inline ErrorCode）</li>
 *   <li>{@link #NopMetadataException(String, Throwable)} — 消息 + cause</li>
 *   <li>{@link #NopMetadataException(ErrorCode)} — 错误码（推荐对外路径）</li>
 *   <li>{@link #NopMetadataException(ErrorCode, Throwable)} — 错误码 + cause</li>
 * </ul>
 *
 * <p>说明：{@link NopException} 仅暴露 {@code (ErrorCode)} / {@code (ErrorCode, Throwable)} 构造器；
 * String 入参的构造器内部用 {@code ErrorCode.define(message, message)} 包成 inline ErrorCode（无 i18n key，
 * 仅承载 message 给 {@code super.getDescription()}）。
 */
public class NopMetadataException extends NopException {
    private static final long serialVersionUID = 1L;

    /**
     * @deprecated Use {@link #NopMetadataException(ErrorCode)} with a proper
     *             {@link NopMetadataErrors} constant instead. String-based construction
     *             creates an inline ErrorCode with no i18n key, polluting GraphQL
     *             errorCode fields.
     */
    @Deprecated
    public NopMetadataException(String message) {
        super(toInlineErrorCode(message));
    }

    /**
     * @deprecated Use {@link #NopMetadataException(ErrorCode, Throwable)} with a proper
     *             {@link NopMetadataErrors} constant instead.
     */
    @Deprecated
    public NopMetadataException(String message, Throwable cause) {
        super(toInlineErrorCode(message), cause);
    }

    public NopMetadataException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NopMetadataException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    private static ErrorCode toInlineErrorCode(String message) {
        // inline ErrorCode：description 与 errorCode 字段都用 message（无 i18n key 场景，仅承载 message）
        return ErrorCode.define(message, message);
    }
}
