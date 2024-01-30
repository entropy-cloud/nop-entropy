/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.ast;

import io.nop.api.core.annotations.biz.BizMakerCheckerMeta;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.auth.ActionAuthMeta;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.ast._gen._GraphQLFieldDefinition;
import io.nop.graphql.core.reflection.IGraphQLArgsNormalizer;
import io.nop.xlang.xmeta.IObjPropMeta;

import java.util.List;
import java.util.stream.Collectors;

public class GraphQLFieldDefinition extends _GraphQLFieldDefinition implements IGraphQLFieldDefinition {
    private IDataFetcher fetcher;

    /**
     * 如果graphql的对象定义是从IObjMeta转换得到，则这里可以保存原始的propMeta，从而获得更多的扩展配置。
     * 如果把所有propMeta上的信息都转换为GraphQL的directive定义，则会产生很多重复性工作。
     */
    private IObjPropMeta propMeta;

    private PropMeta beanPropMeta;

    private Boolean lazy;

    private IFunctionModel functionModel;

    private IServiceAction serviceAction;

    private GraphQLOperationType operationType;

    private ActionAuthMeta auth;

    private String operationName;

    private IClassModel sourceClassModel;

    /**
     * 如果开启maker checker机制，则在执行mutation操作之前，会先尝试执行tryAction。 如果要求审批，则会插入审批记录，并抛出异常，从而中断实际修改动作
     */
    private IServiceAction tryAction;

    private BizMakerCheckerMeta makerCheckerMeta;

    private IGraphQLArgsNormalizer argsNormalizer;

    private int propId;

    @Override
    public GraphQLFieldDefinition deepClone(){
        GraphQLFieldDefinition field = super.deepClone();
        field.setPropMeta(propMeta);
        field.setBeanPropMeta(beanPropMeta);
        field.setPropId(propId);
        return field;
    }

    public int getPropId() {
        return propId;
    }

    public void setPropId(int propId) {
        this.propId = propId;
    }

    public int getPropIdFromMeta() {
        if (propMeta != null) {
            Integer propId = propMeta.getPropId();
            if (propId == null)
                return 0;
            return propId;
        }
        if (beanPropMeta != null)
            return beanPropMeta.propId();
        return 0;
    }

    public void initArgPropId() {
        List<GraphQLArgumentDefinition> args = getArguments();
        if (args != null) {
            for (int i = 0, n = args.size(); i < n; i++) {
                args.get(i).setPropId(i + 1);
            }
        }
    }

    public IGraphQLArgsNormalizer getArgsNormalizer() {
        return argsNormalizer;
    }

    public void setArgsNormalizer(IGraphQLArgsNormalizer argsNormalizer) {
        this.argsNormalizer = argsNormalizer;
    }

    public Boolean getLazy() {
        return lazy;
    }

    public void setLazy(Boolean lazy) {
        this.lazy = lazy;
    }

    public IClassModel getSourceClassModel() {
        return sourceClassModel;
    }

    public void setSourceClassModel(IClassModel sourceClassModel) {
        this.sourceClassModel = sourceClassModel;
    }

    public BizMakerCheckerMeta getMakerCheckerMeta() {
        return makerCheckerMeta;
    }

    public void setMakerCheckerMeta(BizMakerCheckerMeta makerCheckerMeta) {
        this.makerCheckerMeta = makerCheckerMeta;
    }

    public String getTryMethod() {
        return makerCheckerMeta != null ? makerCheckerMeta.getTryMethod() : null;
    }

    public String getCancelMethod() {
        return makerCheckerMeta != null ? makerCheckerMeta.getCancelMethod() : null;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public ActionAuthMeta getAuth() {
        return auth;
    }

    public void setAuth(ActionAuthMeta auth) {
        this.auth = auth;
    }

    public IServiceAction getTryAction() {
        return tryAction;
    }

    public void setTryAction(IServiceAction tryAction) {
        this.tryAction = tryAction;
    }

    public GraphQLOperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(GraphQLOperationType operationType) {
        this.operationType = operationType;
    }

    public IFunctionModel getFunctionModel() {
        return functionModel;
    }

    public void setFunctionModel(IFunctionModel functionModel) {
        checkAllowChange();
        this.functionModel = functionModel;
    }

    public IServiceAction getServiceAction() {
        return serviceAction;
    }

    public void setServiceAction(IServiceAction serviceAction) {
        checkAllowChange();
        this.serviceAction = serviceAction;
    }

    public IDataFetcher getFetcher() {
        return fetcher;
    }

    public void setFetcher(IDataFetcher fetcher) {
        checkAllowChange();
        this.fetcher = fetcher;
    }

    public IObjPropMeta getPropMeta() {
        return propMeta;
    }

    public void setPropMeta(IObjPropMeta propMeta) {
        checkAllowChange();
        this.propMeta = propMeta;
    }

    public PropMeta getBeanPropMeta() {
        return beanPropMeta;
    }

    public void setBeanPropMeta(PropMeta beanPropMeta) {
        checkAllowChange();
        this.beanPropMeta = beanPropMeta;
    }

    public GraphQLArgumentDefinition getArg(String name) {
        List<GraphQLArgumentDefinition> args = getArguments();
        if (args == null)
            return null;
        for (GraphQLArgumentDefinition arg : args) {
            if (arg.getName().equals(name))
                return arg;
        }
        return null;
    }

    public List<String> getArgNames() {
        List<GraphQLArgumentDefinition> args = getArguments();
        if (args == null)
            return null;
        return args.stream().map(GraphQLArgumentDefinition::getName).collect(Collectors.toList());
    }
}