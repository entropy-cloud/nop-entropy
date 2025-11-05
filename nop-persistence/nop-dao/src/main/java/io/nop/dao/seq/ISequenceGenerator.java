/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.seq;

/**
 * 统一管理系统中所有顺序递增的序列号或者随机生成的序列号
 */
public interface ISequenceGenerator {
    /**
     * 根据序列名称获取序列的值。
     *
     * @param seqName    序列的名称
     * @param useDefault 如果seqName指定的序列不存在，是否取seqName=default的序列的值
     */
    long generateLong(String seqName, boolean useDefault);

    default String generateString(String seqName, boolean useDefault) {
        return String.valueOf(generateLong(seqName, useDefault));
    }
}
