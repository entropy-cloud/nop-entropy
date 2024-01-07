
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
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

    @Override
    protected void afterEntityChange(NopDynFunctionMeta entity, IServiceContext context) {
        super.afterEntityChange(entity, context);

        codeGen.generateBizModel(entity.getEntityMeta());
    }
}
