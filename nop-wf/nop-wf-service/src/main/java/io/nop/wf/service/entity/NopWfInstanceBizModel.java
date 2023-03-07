/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.wf.dao.entity.NopWfInstance;

@BizModel("NopWfInstance")
public class NopWfInstanceBizModel extends CrudBizModel<NopWfInstance>{
    public NopWfInstanceBizModel(){
        setEntityName(NopWfInstance.class.getName());
    }
}
