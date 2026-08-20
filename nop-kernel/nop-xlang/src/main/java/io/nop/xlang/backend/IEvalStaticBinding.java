/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.backend;

import io.nop.core.lang.eval.EvalRuntime;

/**
 * 静态资源生成类绑定：以生成类执行体求值（替代解释器执行原树）。
 */
public interface IEvalStaticBinding {

    /** 以生成类执行体求值（scope/输出缓冲经 EvalRuntime 传入） */
    Object execute(EvalRuntime rt);

    /** 绑定的身份证据（如生成类入口 Method / 生成类实例），供后端身份断言与诊断 */
    Object getBindingArtifact();
}
