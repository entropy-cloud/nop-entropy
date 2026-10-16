/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debug.web;

import io.nop.api.core.auth.IActionAuthChecker;
import io.nop.api.core.auth.ISecurityContext;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.auth.IUserContextExtractor;
import io.nop.api.core.exceptions.NopLoginException;

import java.util.Map;
import java.util.UUID;

/**
 * REPL 访问门（D6 §3.4 权限矩阵）：登录态（extractor，null/异常 → 401）+ 权限码
 * （IActionAuthChecker，缺失或 false → 403——缺失 checker 时拒绝，"无鉴权不开放"红线）。
 * ISecurityContext 以 null eval scope 承载（权限面不消费 scope）。
 */
public class ReplAccessGuard {
    public static final String PERM_COMPILE = "nop/xlang/debug/ReplCompile";
    public static final String PERM_EXECUTE = "nop/xlang/debug/ReplExecute";

    private final IUserContextExtractor extractor;
    private final IActionAuthChecker authChecker;

    public ReplAccessGuard(IUserContextExtractor extractor, IActionAuthChecker authChecker) {
        this.extractor = extractor;
        this.authChecker = authChecker;
    }

    /** 鉴权 + 权限检查；通过返回用户上下文，否则抛 401/403 语义异常。 */
    public IUserContext checkAccess(Map<String, Object> headers, String permission) {
        IUserContext ctx = null;
        if (extractor != null) {
            try {
                ctx = extractor.extractFromHeaders(headers);
            } catch (NopLoginException e) {
                ctx = null;
            }
        }
        if (ctx == null)
            throw new ReplAccessException(401, "Authentication required");
        if (authChecker == null || !authChecker.isPermitted(permission, new Context(ctx)))
            throw new ReplAccessException(403, "Permission denied: " + permission);
        return ctx;
    }

    static final class Context implements ISecurityContext {
        private final IUserContext userContext;

        Context(IUserContext userContext) {
            this.userContext = userContext;
        }

        @Override
        public io.nop.core.lang.eval.IEvalScope getEvalScope() {
            return null;
        }

        @Override
        public IUserContext getUserContext() {
            return userContext;
        }
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString();
    }
}
