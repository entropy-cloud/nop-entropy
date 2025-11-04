/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;

/**
 * 单线程执行，嵌套调用不会出现堆栈溢出的情况
 */
public class ContinuationExecutor implements Executor {
    public static final ContinuationExecutor INSTANCE = new ContinuationExecutor();

    static final Logger LOG = LoggerFactory.getLogger(ContinuationExecutor.class);

    private final ThreadLocal<Continuation> continuation = ThreadLocal.withInitial(Continuation::new);

    public void remove(){
        continuation.remove();
    }
    @Override
    public void execute(Runnable command) {
        continuation.get().submit(command);
    }

    public static class Continuation {
        private ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private boolean inLoop;

        public void submit(Runnable task) {
            tasks.add(task);
            if (!inLoop) {
                runLoop();
            }
        }

        private void runLoop() {
            inLoop = true;
            try {
                do {
                    try {
                        tasks.pop().run();
                    } catch (Throwable e) {
                        LOG.error("nop.err.executor.run-continuation-fail", e);
                    }
                } while (!tasks.isEmpty());
            } finally {
                inLoop = false;
            }
            onLoopFinished();
        }

        protected void onLoopFinished(){}
    }
}
