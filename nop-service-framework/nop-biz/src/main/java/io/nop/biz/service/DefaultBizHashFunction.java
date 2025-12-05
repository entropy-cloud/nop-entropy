/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.service;

import io.nop.api.core.beans.ApiMessage;
import io.nop.api.core.biz.IBizHashFunction;
import io.nop.api.core.util.ApiHeaders;
import io.nop.commons.crypto.HashHelper;

public class DefaultBizHashFunction implements IBizHashFunction {
    public static DefaultBizHashFunction INSTANCE = new DefaultBizHashFunction();

    @Override
    public int getBizHash(ApiMessage message) {
        String bizKey = ApiHeaders.getBizKey(message);
        if (bizKey == null)
            return 0;
        return HashHelper.murmur3_32(bizKey);
    }
}
