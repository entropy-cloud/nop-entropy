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

import io.nop.tcc.dao.entity.NopTccBranchRecord;
import io.nop.tcc.biz.INopTccBranchRecordBiz;

@BizModel("NopTccBranchRecord")
public class NopTccBranchRecordBizModel extends CrudBizModel<NopTccBranchRecord> implements INopTccBranchRecordBiz {
    public NopTccBranchRecordBizModel(){
        setEntityName(NopTccBranchRecord.class.getName());
    }
}
