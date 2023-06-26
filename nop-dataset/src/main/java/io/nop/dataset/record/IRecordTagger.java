/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.record;

import java.util.Collection;

/**
 * 获取一条记录对应的标签列表。例如，在splitter内部根据这些tag对记录进行分组
 *
 * @param <T>
 */
public interface IRecordTagger<T,C> {
    Collection<String> getTags(T record, C context);
}