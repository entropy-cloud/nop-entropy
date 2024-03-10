/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.context;

import io.nop.core.lang.eval.IEvalScope;

/**
 * IEvalRuntime的派生类提供强类型的上下文环境对象。
 */
public interface IEvalContext {
    IEvalScope getEvalScope();
}
