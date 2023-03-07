/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core;

import io.nop.api.core.util.ProcessResult;

/**
 * 处理一个批次的数据。包含了loader=>processor=>consumer的完整调用过程。
 * 一般会使用{@link io.nop.batch.core.processor.BatchChunkProcessor}实现类，不会直接用到这个接口
 */
public interface IBatchChunkProcessor {
    ProcessResult process(IBatchChunkContext context);
}