/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.api;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IComponentModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.action.IServiceAction;
import io.nop.fsm.execution.IStateMachine;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.biz.IGraphQLBizObject;
import io.nop.xlang.xmeta.IObjMeta;

import java.util.Collection;
import java.util.Map;

import static io.nop.biz.BizErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.biz.BizErrors.ERR_BIZ_NO_OBJ_META;
import static io.nop.biz.BizErrors.ERR_BIZ_NO_STATE_MACHINE;

/**
 * 由BizObjName参数所唯一定位的聚合模型对象，所有与特定业务对象相关的模型信息以及业务方法都可以查找到。
 * <p>
 * 它是静态对象，不持有非静态属性
 */
public interface IBizObject extends IComponentModel, IGraphQLBizObject {
    String getBizObjName();

    IBizModel getBizModel();

    IObjMeta getObjMeta();

    IStateMachine getStateMachine();

    default void triggerStateChange(Object bean, String event, IServiceContext context) {
        IStateMachine stm = getStateMachine();
        if (stm == null)
            throw new NopException(ERR_BIZ_NO_STATE_MACHINE).param(ARG_BIZ_OBJ_NAME, getBizObjName());
        stm.triggerStateChange(bean, event, context);
    }

    default IObjMeta requireObjMeta() {
        IObjMeta objMeta = getObjMeta();
        if (objMeta == null)
            throw new NopException(ERR_BIZ_NO_OBJ_META).param(ARG_BIZ_OBJ_NAME, getBizObjName());
        return objMeta;
    }

    default IBizActionModel getActionModel(String action) {
        return getBizModel().getAction(action);
    }

    GraphQLOperationType getOperationType(String action);

    IServiceAction getAction(String action);

    IServiceAction requireAction(String action);

    default Object invoke(String action, Object request, FieldSelectionBean selection, IServiceContext context) {
        IServiceAction serviceAction = requireAction(action);
        return serviceAction.invoke(request, selection, context);
    }

    GraphQLFieldDefinition getOperationDefinition(GraphQLOperationType opType, String name);

    Collection<GraphQLFieldDefinition> getOperationDefinitions(GraphQLOperationType opType);

    Map<String, GraphQLFieldDefinition> getOperationDefinitions();

    GraphQLObjectDefinition getObjectDefinition();

    <T> T asProxy();
}