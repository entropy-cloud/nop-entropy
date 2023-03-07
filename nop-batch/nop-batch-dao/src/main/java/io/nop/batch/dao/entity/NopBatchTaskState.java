/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.batch.dao.entity._gen._NopBatchTaskState;

import io.nop.batch.dao.entity._gen.NopBatchTaskStatePkBuilder;


@BizObjName("NopBatchTaskState")
public class NopBatchTaskState extends _NopBatchTaskState{
    public NopBatchTaskState(){
    }


    public static NopBatchTaskStatePkBuilder newPk(){
        return new NopBatchTaskStatePkBuilder();
    }

}
