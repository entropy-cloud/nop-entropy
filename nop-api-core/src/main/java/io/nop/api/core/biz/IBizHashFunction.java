/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.biz;

import io.nop.api.core.beans.ApiMessage;

/**
 * 根据业务对象中包含的信息，计算出一个用于数据分区或者路由选择的hash值
 */
public interface IBizHashFunction {
    int getBizHash(ApiMessage message);
}
