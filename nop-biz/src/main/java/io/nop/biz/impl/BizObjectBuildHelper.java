/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.impl;

import io.nop.api.core.util.OrderedComparator;
import io.nop.biz.decorator.IActionDecoratorCollector;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.context.action.IServiceActionDecorator;
import io.nop.core.reflect.IFunctionModel;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.biz.IGraphQLBizObject;
import io.nop.graphql.core.fetcher.ServiceActionFetcher;
import io.nop.graphql.core.reflection.GraphQLBizModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BizObjectBuildHelper {

    public static void addDefaultAction(IGraphQLBizObject bizObj, GraphQLBizModel bizModel, List<IActionDecoratorCollector> collectors) {
        bizModel.getMutationActions().forEach((name, action) -> {
            buildFetcher(action, collectors);
            // 如果xbiz文件中已经定义，则忽略java类上的定义
            if (bizObj.isAllowInheritAction(name)) {
                GraphQLFieldDefinition op = bizObj.getOperations().get(name);
                if (op == null) {
                    bizObj.addOperation(name, action);
                } else {
                    if (op.getServiceAction() == null) {
                        op.setServiceAction(action.getServiceAction());
                        op.setFetcher(action.getFetcher());
                    }
                    if(op.getArguments() == null){
                        op.setArguments(action.cloneArguments());
                    }
                }
            }
        });

        bizModel.getQueryActions().forEach((name, action) -> {
            buildFetcher(action, collectors);
            if (bizObj.isAllowInheritAction(name)) {
                GraphQLFieldDefinition op = bizObj.getOperations().get(name);
                if (op == null) {
                    bizObj.addOperation(name, action);
                } else {
                    if (op.getServiceAction() == null) {
                        op.setServiceAction(action.getServiceAction());
                        op.setFetcher(action.getFetcher());
                    }
                    if(op.getArguments() == null){
                        op.setArguments(action.cloneArguments());
                    }
                }
            }
        });

        bizModel.getBizActions().forEach((name, action) -> {
            // 如果xbiz文件中已经定义，则忽略java类上的定义
            if (bizObj.isAllowInheritAction(name) && !bizObj.getActions().containsKey(name)) {
                bizObj.addAction(name, action);
            }
        });
    }

    private static void buildFetcher(GraphQLFieldDefinition field, List<IActionDecoratorCollector> collectors) {
        IServiceAction action = field.getServiceAction();
        if (action == null)
            return;

        List<IServiceActionDecorator> decorators = buildDecorators(field.getFunctionModel(), collectors);

        for (IServiceActionDecorator decorator : decorators) {
            action = decorator.decorate(action);
        }
        field.setFetcher(new ServiceActionFetcher(action));
    }

    private static List<IServiceActionDecorator> buildDecorators(IFunctionModel func, List<IActionDecoratorCollector> collectors) {
        List<IServiceActionDecorator> decorators = new ArrayList<>();
        if (collectors != null) {
            for (IActionDecoratorCollector collector : collectors) {
                collector.collectDecorator(func, decorators);
            }
            Collections.sort(decorators, OrderedComparator.instance());
        }
        return decorators;
    }
}
