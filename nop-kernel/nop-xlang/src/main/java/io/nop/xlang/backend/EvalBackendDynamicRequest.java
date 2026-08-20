/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.backend;

import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;

/**
 * 动态路径求值请求：待执行树 + 求值现场 + resourcePath 提示（可空）。
 */
public final class EvalBackendDynamicRequest {

    private final IExecutableExpression tree;

    private final EvalRuntime runtime;

    private final String resourcePath;

    public EvalBackendDynamicRequest(IExecutableExpression tree, EvalRuntime runtime, String resourcePath) {
        this.tree = tree;
        this.runtime = runtime;
        this.resourcePath = resourcePath;
    }

    public IExecutableExpression getTree() {
        return tree;
    }

    public EvalRuntime getRuntime() {
        return runtime;
    }

    /** 树的源资源路径提示（可空；动态源无 resourcePath） */
    public String getResourcePath() {
        return resourcePath;
    }
}
