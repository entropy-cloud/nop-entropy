/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

/**
 * 获取数据记录中的唯一属性，重复执行时可以识别出已经被处理过的记录，自动跳过，避免重复处理
 *
 * @param <S> 数据记录类型
 */
public interface IBatchRecordKeyExtractor<S> {
    String getRecordKey(S record);
}
