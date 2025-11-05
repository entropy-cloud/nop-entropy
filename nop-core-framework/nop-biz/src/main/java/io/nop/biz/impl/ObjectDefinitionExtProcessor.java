/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.impl;

import io.nop.api.core.annotations.biz.BizMakerCheckerMeta;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.makerchecker.IMakerCheckerProvider;
import io.nop.biz.makerchecker.MakerCheckerTryServiceAction;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.action.IServiceAction;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.fetcher.BeanPropertyFetcher;
import io.nop.graphql.core.fetcher.DictLabelFetcherProvider;
import io.nop.graphql.core.fetcher.EvalActionTransformFetcher;
import io.nop.xlang.xmeta.IObjPropMeta;

import java.util.Collection;

import static io.nop.graphql.core.GraphQLErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_CANCEL_METHOD;
import static io.nop.graphql.core.GraphQLErrors.ARG_OPERATION_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_TRY_METHOD;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_CANCEL_METHOD;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_TRY_METHOD;

public class ObjectDefinitionExtProcessor {
    public static void provideFetchers(GraphQLObjectDefinition objDef) {
        for (GraphQLFieldDefinition fieldDef : objDef.getFields()) {
            provideFetcher(objDef, fieldDef);

            IObjPropMeta propMeta = fieldDef.getPropMeta();
            if (propMeta != null) {
                if (propMeta.getTransformOut() != null) {
                    IDataFetcher fetcher = fieldDef.getFetcher();
                    if (fetcher == null)
                        fetcher = BeanPropertyFetcher.INSTANCE;
                    fieldDef.setFetcher(
                            new EvalActionTransformFetcher(fetcher, propMeta.getTransformOut()));
                }
            }
        }
    }

    static void provideFetcher(GraphQLObjectDefinition objDef, GraphQLFieldDefinition fieldDef) {
        IDataFetcher fetcher = fieldDef.getFetcher();

        // 已经指定了fetcher，则以指定的为准
        if (fetcher != null && fetcher != BeanPropertyFetcher.INSTANCE)
            return;

        DictLabelFetcherProvider.INSTANCE.provideFetcher(objDef, fieldDef);
    }

    public static void initMakerChecker(BizObjectImpl bizObj, IMakerCheckerProvider makerCheckerProvider) {
        Collection<GraphQLFieldDefinition> operations = bizObj.getOperationDefinitions().values();
        for (GraphQLFieldDefinition operation : operations) {
            BizMakerCheckerMeta makerChecker = operation.getMakerCheckerMeta();
            if (makerChecker != null) {
                IServiceAction tryAction = bizObj.getAction(makerChecker.getTryMethod());
                if (tryAction == null)
                    throw new NopException(ERR_GRAPHQL_UNKNOWN_TRY_METHOD).source(operation)
                            .param(ARG_OPERATION_NAME, operation.getName())
                            .param(ARG_TRY_METHOD, makerChecker.getTryMethod())
                            .param(ARG_BIZ_OBJ_NAME, bizObj.getBizObjName());

                if (!StringHelper.isEmpty(makerChecker.getCancelMethod())) {
                    if (bizObj.getAction(makerChecker.getCancelMethod()) == null)
                        throw new NopException(ERR_GRAPHQL_UNKNOWN_CANCEL_METHOD).source(operation)
                                .param(ARG_OPERATION_NAME, operation.getName())
                                .param(ARG_CANCEL_METHOD, makerChecker.getCancelMethod())
                                .param(ARG_BIZ_OBJ_NAME, bizObj.getBizObjName());
                }

                operation.setTryAction(new MakerCheckerTryServiceAction(makerCheckerProvider, tryAction, makerChecker,
                        operation.getName()));
            }
        }
    }
}
