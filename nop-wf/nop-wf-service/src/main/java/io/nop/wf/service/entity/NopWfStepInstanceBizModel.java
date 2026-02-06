/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.wf.dao.entity.NopWfStepInstance;
import io.nop.wf.biz.INopWfStepInstanceBiz;

@BizModel("NopWfStepInstance")
public class NopWfStepInstanceBizModel extends CrudBizModel<NopWfStepInstance> implements INopWfStepInstanceBiz {
    public NopWfStepInstanceBizModel(){
        setEntityName(NopWfStepInstance.class.getName());
    }
}
