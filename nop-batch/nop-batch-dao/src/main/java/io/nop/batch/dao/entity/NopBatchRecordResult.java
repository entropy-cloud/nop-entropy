/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.batch.dao.entity._gen._NopBatchRecordResult;

import io.nop.batch.dao.entity._gen.NopBatchRecordResultPkBuilder;


@BizObjName("NopBatchRecordResult")
public class NopBatchRecordResult extends _NopBatchRecordResult{
    public NopBatchRecordResult(){
    }


    public static NopBatchRecordResultPkBuilder newPk(){
        return new NopBatchRecordResultPkBuilder();
    }

}
