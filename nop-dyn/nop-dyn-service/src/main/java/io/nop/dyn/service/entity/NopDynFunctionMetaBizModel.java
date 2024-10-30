/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.dyn.dao.entity.NopDynFunctionMeta;
import io.nop.dyn.service.codegen.DynCodeGen;
import jakarta.inject.Inject;

@BizModel("NopDynFunctionMeta")
public class NopDynFunctionMetaBizModel extends CrudBizModel<NopDynFunctionMeta> {

    @Inject
    DynCodeGen codeGen;

    public NopDynFunctionMetaBizModel() {
        setEntityName(NopDynFunctionMeta.class.getName());
    }

    @BizAction
    @Override
    protected void afterEntityChange(@Name("entity") NopDynFunctionMeta entity, IServiceContext context, @Name("action") String action) {
        super.afterEntityChange(entity, context, action);

        codeGen.generateBizModel(entity.getEntityMeta());
    }
}
