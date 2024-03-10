/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import java.util.Map;

/**
 * 用于计算输入/输出文件的汇总信息
 */
public interface IBatchAggregator<R, B, V> {
    /**
     * 根据header信息和任务上下文信息创建汇总对象
     *
     * @param header  header信息
     * @param context 任务上下文
     * @return 用于保存汇总信息的状态对象
     */
    B createCombinedValue(Map<String, Object> header, IBatchTaskContext context);

    /**
     * 处理每一条记录，并更新汇总对象
     *
     * @param record        记录
     * @param combinedValue 汇总对象
     */
    void aggregate(R record, B combinedValue);

    /**
     * 根据汇总对象和补充的trailer信息，生成汇总结果对象
     *
     * @param trailer       额外补充的trailer信息
     * @param combinedValue 处理过程中持有的汇总状态信息
     * @return 汇总结果对象
     */
    default V complete(Map<String, Object> trailer, B combinedValue) {
        return (V) combinedValue;
    }
}