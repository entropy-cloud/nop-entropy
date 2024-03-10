/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.auth.dao.entity.NopAuthDept;
import io.nop.biz.crud.CrudBizModel;

@BizModel("NopAuthDept")
public class NopAuthDeptBizModel extends CrudBizModel<NopAuthDept> {
    public NopAuthDeptBizModel() {
        setEntityName(NopAuthDept.class.getName());
    }
}
