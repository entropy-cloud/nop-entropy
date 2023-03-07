/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.sql.SQL;
import io.nop.core.unittest.BaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.orm.IOrmTemplate;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

@NopTestConfig(localDb = true)
public class TestBizObject extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IOrmTemplate ormTemplate;

    @Test
    public void testFindPage() {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery("query { NopAuthUser__findPage{ items{ id, userName } } }");
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        graphQLEngine.executeGraphQL(context);
    }

    @Test
    public void testOrderBy() {
        SQL sql = SQL.begin().sql(
                        "select o.dept.deptName, o.position.name from io.nop.auth.dao.entity.NopAuthUser o order by o.position.name")
                .end();
        ormTemplate.findAll(sql);
    }

    @Test
    public void testOrderBy2() {
        BaseTestCase.forceStackTrace();
        SQL sql = SQL.begin().sql(
                        "select o from io.nop.auth.dao.entity.NopAuthDept o " + "where o.parentId is null order by o.id asc")
                .end();
        ormTemplate.findAll(sql);
    }
}
