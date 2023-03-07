/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.auth.dao.entity.NopAuthPosition;
import io.nop.biz.crud.CrudBizModel;

@BizModel("NopAuthPosition")
public class NopAuthPositionBizModel extends CrudBizModel<NopAuthPosition> {
    public NopAuthPositionBizModel() {
        setEntityName(NopAuthPosition.class.getName());
    }
}
