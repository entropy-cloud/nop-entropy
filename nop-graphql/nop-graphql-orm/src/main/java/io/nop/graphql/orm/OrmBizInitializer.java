/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.orm;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.dao.api.IDaoProvider;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.biz.IGraphQLBizInitializer;
import io.nop.orm.IOrmTemplate;

import jakarta.inject.Inject;
import java.util.function.BiConsumer;

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
    public void initialize(GraphQLObjectDefinition objDef, String entityName, BiConsumer<QueryBean, IDataFetchingEnvironment> queryProcessor) {
        new OrmFetcherBuilder(ormTemplate,daoProvider,queryProcessor).initFetchers(objDef,entityName);
    }
}
