/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.debug;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;

public interface IDebugNotifier {
    void notifyWarn(String message, Object... args);

    void notifyLog(SuspendedThread thread, String sourcePath, int line, String message);

    void notifySuspend(SuspendedThread thread, SourceLocation loc, IEvalScope scope);

    void notifyStepInto(SuspendedThread thread, SourceLocation loc, IEvalScope scope);

    void notifyStepOver(SuspendedThread thread, SourceLocation loc, IEvalScope scope);

    void notifyStepOut(SuspendedThread thread, SourceLocation loc, IEvalScope scope);

    void notifyBreakAt(SuspendedThread thread, SourceLocation loc, IEvalScope scope);
}
