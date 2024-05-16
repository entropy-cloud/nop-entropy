/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.orm;

import io.nop.commons.util.StringHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.biz.IBizObjectQueryProcessorBuilder;
import io.nop.graphql.core.biz.IGraphQLBizInitializer;
import io.nop.graphql.core.biz.IGraphQLBizObject;
import io.nop.graphql.core.schema.TypeRegistry;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;

public class OrmBizInitializer implements IGraphQLBizInitializer {

    private IOrmTemplate ormTemplate;
    private IDaoProvider daoProvider;

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Override
    public void initialize(IGraphQLBizObject bizObj,
                           IBizObjectQueryProcessorBuilder queryProcessorBuilder,
                           TypeRegistry typeRegistry) {
        GraphQLObjectDefinition objDef = bizObj.getObjectDefinition();
        String entityName = bizObj.getEntityName();
        if (StringHelper.isEmpty(entityName))
            return;

        new OrmFetcherBuilder(ormTemplate, daoProvider, queryProcessorBuilder).initFetchers(objDef, entityName);
    }
}
