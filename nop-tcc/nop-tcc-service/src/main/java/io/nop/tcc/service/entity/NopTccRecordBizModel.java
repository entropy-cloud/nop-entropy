/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.tcc.dao.entity.NopTccRecord;
import io.nop.tcc.biz.INopTccRecordBiz;

@BizModel("NopTccRecord")
public class NopTccRecordBizModel extends CrudBizModel<NopTccRecord> implements INopTccRecordBiz {
    public NopTccRecordBizModel(){
        setEntityName(NopTccRecord.class.getName());
    }
}
