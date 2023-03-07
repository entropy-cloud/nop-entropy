/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.tcc.core.meta;

import java.io.Serializable;

/**
 * 在java接口上标注的TCC事务相关元数据
 */
public interface ITccServiceMeta extends Serializable {
    String getServiceName();

    TccMethodMeta getMethodMeta(String serviceAction);
}
