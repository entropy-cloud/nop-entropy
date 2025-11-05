/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalRuntime;

public interface IDebugNotifier {
    void notifyWarn(String message, Object... args);

    void notifyLog(SuspendedThread thread, String sourcePath, int line, String message);

    void notifySuspend(SuspendedThread thread, SourceLocation loc, EvalRuntime rt);

    void notifyStepInto(SuspendedThread thread, SourceLocation loc, EvalRuntime rt);

    void notifyStepOver(SuspendedThread thread, SourceLocation loc, EvalRuntime rt);

    void notifyStepOut(SuspendedThread thread, SourceLocation loc, EvalRuntime rt);

    void notifyBreakAt(SuspendedThread thread, SourceLocation loc, EvalRuntime rt);
}
