/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.util.progress;

public class StepProgressListener implements IStepProgressListener {
    private final IProgressListener listener;

    private final Object message;
    private final long total;
    private long progress;

    public StepProgressListener(IProgressListener listener, Object message, long total, long progress) {
        this.listener = listener;
        this.message = message;
        this.progress = progress;
        this.total = total;
    }

    public void begin() {
        listener.onProgress(message, progress, total);
    }

    public void end() {
        if (progress != total)
            listener.onProgress(message, total, total);
    }

    public void onStep(long step) {
        progress += step;
        listener.onProgress(message, progress, total);
    }

    public IProgressListener getListener() {
        return listener;
    }

    public Object getMessage() {
        return message;
    }

    public long getTotal() {
        return total;
    }

    public long getProgress() {
        return progress;
    }
}