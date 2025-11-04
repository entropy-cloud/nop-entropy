/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.record;

/**
 * 从数据文件中读取的记录如果要保留行号，则可以实现此接口。
 */
public interface IRowNumberRecord {
    /**
     * 数据记录在结果集中对应的行号，从1开始。
     */
    long getRecordRowNumber();

    void setRecordRowNumber(long rowNumber);

}