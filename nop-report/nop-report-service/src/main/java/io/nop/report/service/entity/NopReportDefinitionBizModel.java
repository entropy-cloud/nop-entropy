/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.report.dao.entity.NopReportDefinition;
import io.nop.report.biz.INopReportDefinitionBiz;

@BizModel("NopReportDefinition")
public class NopReportDefinitionBizModel extends CrudBizModel<NopReportDefinition> implements INopReportDefinitionBiz {
    public NopReportDefinitionBizModel(){
        setEntityName(NopReportDefinition.class.getName());
    }
}
