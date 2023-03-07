/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.util.progress;

/**
 * 用于封装进度条通知
 */
public interface IProgressListener {

    boolean isCancelled();

    void onProgress(Object message, long progress, long total);

    default IStepProgressListener toStepListener(Object message, long total) {
        return new StepProgressListener(this, message, total, 0);
    }
}