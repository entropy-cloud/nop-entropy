/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.impl;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizObject;
import io.nop.biz.model.BizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.hook.IMethodMissingHook;
import io.nop.fsm.execution.IStateMachine;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.orm.model.OrmModelConstants;
import io.nop.xlang.xmeta.IObjMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.nop.biz.BizErrors.ERR_BIZ_ACTION_ARG_NOT_FIELD_SELECTION;
import static io.nop.biz.BizErrors.ERR_BIZ_ACTION_ARG_NOT_SERVICE_CONTEXT;
import static io.nop.biz.BizErrors.ERR_BIZ_OBJECT_NOT_SUPPORT_ACTION;
import static io.nop.graphql.core.GraphQLErrors.ARG_ACTION_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_EXPECTED_OPERATION_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ARG_OPERATION_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OPERATION_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNEXPECTED_OPERATION_TYPE;

public class BizObjectImpl implements IBizObject, IMethodMissingHook {
    private SourceLocation location;
    private final String bizObjName;
    private BizModel bizModel;
    private IObjMeta objMeta;
    private IStateMachine stateMachine;
    private GraphQLObjectDefinition objectDefinition;

    private Map<String, GraphQLFieldDefinition> operations = Collections.emptyMap();

    private Map<String, IServiceAction> actions = Collections.emptyMap();

    public BizObjectImpl(String bizObjName) {
        this.bizObjName = bizObjName;
    }

    @Override
    public Object getExtAttribute(String name) {
        if (bizModel == null)
            return null;
        return bizModel.prop_get(name);
    }

    @Override
    public boolean isAllowInheritAction(String action) {
        if (bizModel == null)
            return true;

        if (bizModel.getDisabledActions() != null) {
            if (bizModel.getDisabledActions().contains(action))
                return false;
        }

        if (bizModel.getInheritActions() != null && !bizModel.getInheritActions().isEmpty()) {
            return bizModel.getInheritActions().contains(action);
        }
        return true;
    }

    @Override
    public String getEntityName() {
        String entityName = null;
        if (getObjMeta() != null)
            entityName = getObjMeta().getEntityName();
        return entityName;
    }

    @Override
    public Map<String, GraphQLFieldDefinition> getOperations() {
        return operations;
    }

    @Override
    public Map<String, IServiceAction> getActions() {
        return actions;
    }

    @Override
    public IStateMachine getStateMachine() {
        return stateMachine;
    }

    public void setStateMachine(IStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    @Override
    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    @Override
    public BizModel getBizModel() {
        return bizModel;
    }

    public void setBizModel(BizModel bizModel) {
        this.bizModel = bizModel;
    }

    @Override
    public IObjMeta getObjMeta() {
        return objMeta;
    }

    public void setObjMeta(IObjMeta objMeta) {
        this.objMeta = objMeta;
    }

    public void setOperations(Map<String, GraphQLFieldDefinition> operations) {
        this.operations = operations;
    }

    public void setActions(Map<String, IServiceAction> actions) {
        this.actions = actions;
    }

    @Override
    public String getBizObjName() {
        return bizObjName;
    }

    @Override
    public GraphQLObjectDefinition getObjectDefinition() {
        return objectDefinition;
    }

    public void setObjectDefinition(GraphQLObjectDefinition objectDefinition) {
        this.objectDefinition = objectDefinition;
    }

    @Override
    public IServiceAction getAction(String action) {
        GraphQLFieldDefinition operation = operations.get(action);
        if (operation != null)
            return operation.getServiceAction();
        return actions.get(action);
    }

    @Override
    public GraphQLFieldDefinition getOperationDefinition(GraphQLOperationType opType, String name) {
        // 不对外暴露为GraphQL服务
        if (bizModel != null && bizModel.containsTag(OrmModelConstants.TAG_NOT_PUB))
            return null;

        GraphQLFieldDefinition operation = operations.get(name);
        if (operation == null)
            return null;
        if (opType != null && operation.getOperationType() != opType)
            throw new NopException(ERR_GRAPHQL_UNEXPECTED_OPERATION_TYPE)
                    .param(ARG_OPERATION_NAME, getBizObjName() + "__" + name)
                    .param(ARG_OPERATION_TYPE, operation.getOperationType()).param(ARG_EXPECTED_OPERATION_TYPE, opType);
        return operation;
    }

    @Override
    public Collection<GraphQLFieldDefinition> getOperationDefinitions(GraphQLOperationType opType) {
        // 不对外暴露为GraphQL服务
        if (bizModel != null && bizModel.containsTag(OrmModelConstants.TAG_NOT_PUB))
            return Collections.emptyList();

        List<GraphQLFieldDefinition> ret = new ArrayList<>();
        for (GraphQLFieldDefinition op : operations.values()) {
            if (op.getOperationType() == opType) {
                ret.add(op);
            }
        }
        return ret;
    }

    @Override
    public Map<String, GraphQLFieldDefinition> getOperationDefinitions() {
        return operations;
    }

    @Override
    public Object method_invoke(String actionName, Object[] args, IEvalScope scope) {
        IServiceAction action = getAction(actionName);
        if (action == null) {
            throw new NopException(ERR_BIZ_OBJECT_NOT_SUPPORT_ACTION).param(ARG_BIZ_OBJ_NAME, bizObjName)
                    .param(ARG_ACTION_NAME, actionName);
        }

        Object request = args[0];
        FieldSelectionBean selection = null;
        IServiceContext context = null;
        if (request instanceof ApiRequest<?>) {
            ApiRequest<?> req = (ApiRequest<?>) request;
            selection = req.getSelection();
            request = req.getData();

            if (args.length > 1) {
                if (!(args[1] instanceof IServiceContext))
                    throw new NopException(ERR_BIZ_ACTION_ARG_NOT_SERVICE_CONTEXT).param(ARG_BIZ_OBJ_NAME, bizObjName)
                            .param(ARG_ACTION_NAME, actionName);
                context = (IServiceContext) args[1];
            }
        } else if (args.length > 1) {
            if (args[1] != null) {
                if (!(args[1] instanceof FieldSelectionBean))
                    throw new NopException(ERR_BIZ_ACTION_ARG_NOT_FIELD_SELECTION).param(ARG_BIZ_OBJ_NAME, bizObjName)
                            .param(ARG_ACTION_NAME, actionName);

                selection = (FieldSelectionBean) args[1];
            }
            if (args.length > 2) {
                if (!(args[2] instanceof IServiceContext))
                    throw new NopException(ERR_BIZ_ACTION_ARG_NOT_SERVICE_CONTEXT).param(ARG_BIZ_OBJ_NAME, bizObjName)
                            .param(ARG_ACTION_NAME, actionName);
                context = (IServiceContext) args[2];
            } else {
                throw new IllegalArgumentException("nop.err.graphql.too-many-action-args:bizObjName=" + getBizObjName()
                        + ",bizAction=" + actionName);
            }
        } else {
            context = (IServiceContext) scope.getValue(BizConstants.ACTION_ARG_SVC_CONTEXT);
        }
        if (context == null)
            throw new IllegalArgumentException(
                    "nop.err.graphql.no-svc-context:bizObName=" + getBizObjName() + ",bizAction=" + actionName);

        return action.invoke(request, selection, context);
    }
}
