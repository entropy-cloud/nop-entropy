/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.text;

import io.nop.api.core.beans.IntRangeBean;

public interface ITextPattern {
    /**
     * 在字符串内查找文本模式
     *
     * @param str
     * @param fromIndex 查找起始位置
     * @param toIndex   如果大于0，则表示查找截止位置
     * @return
     */
    IntRangeBean search(CharSequence str, int fromIndex, int toIndex);
}