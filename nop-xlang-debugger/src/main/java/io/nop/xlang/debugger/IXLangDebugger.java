/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.debugger.IDebugger;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalOutput;

public interface IXLangDebugger extends IDebugger {
    void checkBreakpoint(@Name("loc") SourceLocation loc, EvalRuntime rt);
}
