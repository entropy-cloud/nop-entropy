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
import io.nop.biz.BizConstants;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.dyn.dao.entity.NopDynFunctionMeta;
import io.nop.dyn.service.codegen.DynCodeGen;
import io.nop.dyn.biz.INopDynFunctionMetaBiz;
import jakarta.inject.Inject;

@BizModel("NopDynFunctionMeta")
public class NopDynFunctionMetaBizModel extends CrudBizModel<NopDynFunctionMeta> implements INopDynFunctionMetaBiz {

    @Inject
    DynCodeGen codeGen;

    public NopDynFunctionMetaBizModel() {
        setEntityName(NopDynFunctionMeta.class.getName());
    }

    @BizAction
    @Override
    protected void afterEntityChange(@Name("entity") NopDynFunctionMeta entity, @Name("action") String action, IServiceContext context) {
        super.afterEntityChange(entity, action, context);

        if (BizConstants.METHOD_DELETE.equals(action)) {
            // 删除时不再校验source（否则source非法的函数无法被删除），并且要从集合中移除后再重新生成，
            // 否则dao删除不会同步更新父对象的内存集合，已删除函数会残留在重新生成的xbiz中
            entity.getEntityMeta().getFunctionMetas().remove(entity);
        } else {
            entity.validateSource();
            entity.getEntityMeta().getFunctionMetas().add(entity);
        }

        // 函数级变更不涉及实体结构（列/关系），无需触发ORM模型全量重载
        codeGen.generateBizModel(entity.getEntityMeta(), true, false);
    }
}
