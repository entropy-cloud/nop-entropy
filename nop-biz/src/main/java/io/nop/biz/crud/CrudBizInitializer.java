/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.crud;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizObjectManager;
import io.nop.biz.decorator.IActionDecoratorCollector;
import io.nop.biz.impl.BizObjectBuildHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.graphql.core.biz.IBizObjectQueryProcessorBuilder;
import io.nop.graphql.core.biz.IGraphQLBizInitializer;
import io.nop.graphql.core.biz.IGraphQLBizObject;
import io.nop.graphql.core.reflection.GraphQLBizModel;
import io.nop.graphql.core.reflection.ReflectionBizModelBuilder;
import io.nop.graphql.core.schema.TypeRegistry;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;

public class CrudBizInitializer implements IGraphQLBizInitializer {

    private IDaoProvider daoProvider;
    private ITransactionTemplate transactionTemplate;

    private IBizObjectManager bizObjectManager;

    private List<IActionDecoratorCollector> collectors;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setBizObjectManager(IBizObjectManager bizObjectManager) {
        this.bizObjectManager = bizObjectManager;
    }

    @Inject
    public void setTransactionTemplate(ITransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public void setDecoratorCollectors(List<IActionDecoratorCollector> collectors) {
        this.collectors = collectors;
    }

    private DynamicCrudBizModel newBizModelBean(IGraphQLBizObject bizObj) {
        DynamicCrudBizModel biz = new DynamicCrudBizModel();
        biz.setBizObjName(bizObj.getBizObjName());
        biz.setEntityName(bizObj.getEntityName());
        biz.setDaoProvider(daoProvider);
        biz.setTransactionTemplate(transactionTemplate);
        biz.setBizObjectManager(bizObjectManager);
        return biz;
    }

    @Override
    public int order() {
        return NORMAL_PRIORITY - 100;
    }

    @Override
    public void initialize(IGraphQLBizObject bizObj,
                           IBizObjectQueryProcessorBuilder queryProcessorBuilder,
                           TypeRegistry typeRegistry) {
        Set<String> base = ConvertHelper.toCsvSet(bizObj.getExtAttribute(BizConstants.GRAPHQL_BASE_NAME));
        if (base != null && base.contains(BizConstants.BASE_CRUD)) {
            DynamicCrudBizModel bean = newBizModelBean(bizObj);
            GraphQLBizModel bizModel = ReflectionBizModelBuilder.INSTANCE.build(bean, typeRegistry);

            BizObjectBuildHelper.addDefaultAction(bizObj, bizModel, collectors);
        }
    }

}
