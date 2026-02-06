/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.context.ContextProvider;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.sys.dao.entity.NopSysUserVariable;
import io.nop.sys.biz.INopSysUserVariableBiz;

import static io.nop.biz.BizConstants.BIZ_OBJ_NAME_THIS_OBJ;

@BizModel("NopSysUserVariable")
public class NopSysUserVariableBizModel extends CrudBizModel<NopSysUserVariable> implements INopSysUserVariableBiz {
    public NopSysUserVariableBizModel() {
        setEntityName(NopSysUserVariable.class.getName());
    }

    @BizQuery
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public NopSysUserVariable getVar(@Name("varName") String varName, IServiceContext context) {
        Object id = NopSysUserVariable.newPk().setUserId(ContextProvider.currentUserId()).setVarName(varName).build();
        NopSysUserVariable var = dao().getEntityById(id);
        return var;
    }

    @BizMutation
    public void setVar(@Name("varName") String varName,
                       @Name("varValue") String varValue,
                       @Name("varType") String varType,
                       @Name("stdDomain") String stdDomain,
                       IServiceContext context) {

        checkMandatoryParam("setVar", "varName", varName);

        Object id = NopSysUserVariable.newPk().setUserId(ContextProvider.currentUserId()).setVarName(varName).build();
        NopSysUserVariable var = dao().getEntityById(id);
        if (var == null) {
            var = new NopSysUserVariable();
            var.setUserId(ContextProvider.currentUserId());
            var.setVarName(varName);
            dao().saveEntity(var);
        }
        var.setVarValue(varValue);

        if (varType != null)
            var.setVarType(varType);

        if (stdDomain != null)
            var.setStdDomain(stdDomain);
    }
}
