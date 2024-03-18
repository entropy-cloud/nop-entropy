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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public class CollectNotifier implements IDebugNotifier {
    private BlockingQueue<String> messages = new LinkedBlockingDeque<>();

    public BlockingQueue<String> getMessages() {
        return messages;
    }

    @Override
    public void notifyWarn(String message, Object... args) {

    }

    @Override
    public void notifyLog(SuspendedThread thread, String sourcePath, int line, String message) {

    }

    @Override
    public void notifySuspend(SuspendedThread thread, SourceLocation loc, EvalRuntime rt) {
        messages.add("suspend:" + loc);
    }

    @Override
    public void notifyStepInto(SuspendedThread thread, SourceLocation loc, EvalRuntime rt) {
        messages.add("stepInto:" + loc);
    }

    @Override
    public void notifyStepOver(SuspendedThread thread, SourceLocation loc, EvalRuntime rt) {
        messages.add("stepOver:" + loc);
    }

    @Override
    public void notifyStepOut(SuspendedThread thread, SourceLocation loc, EvalRuntime rt) {
        messages.add("stepOut:" + loc);
    }

    @Override
    public void notifyBreakAt(SuspendedThread thread, SourceLocation loc, EvalRuntime rt) {
        messages.add("break:" + loc);
    }
}
