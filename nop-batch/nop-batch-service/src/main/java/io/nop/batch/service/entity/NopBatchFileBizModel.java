/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.batch.dao.entity.NopBatchFile;
import io.nop.batch.biz.INopBatchFileBiz;

@BizModel("NopBatchFile")
public class NopBatchFileBizModel extends CrudBizModel<NopBatchFile> implements INopBatchFileBiz {
    public NopBatchFileBizModel(){
        setEntityName(NopBatchFile.class.getName());
    }
}
