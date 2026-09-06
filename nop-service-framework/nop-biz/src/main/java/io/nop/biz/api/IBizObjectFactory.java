/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.api;

import io.nop.biz.impl.BizObjectImpl;
import io.nop.biz.model.BizModel;
import io.nop.xlang.xmeta.IObjMeta;

public interface IBizObjectFactory {
    BizObjectImpl newBizObject(String bizObjName, BizModel bizModel, IObjMeta objMeta);
}
