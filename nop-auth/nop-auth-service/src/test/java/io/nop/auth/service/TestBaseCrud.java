/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.auth.dao.entity.NopAuthDept;
import io.nop.auth.dao.entity.NopAuthGroup;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = true)
public class TestBaseCrud extends JunitBaseTestCase {
    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    /**
     * 只定义biz和meta文件，在xbiz根节点上标注graphql:base="crud"
     */
    @Test
    public void testFindPage() {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery("query { NopAuthUserEx__findPage{ items{ id, userName } } }");
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        GraphQLResponseBean response = graphQLEngine.executeGraphQL(context);
        assertTrue(!response.hasError());
    }

    @Test
    public void testActiveFindPage() {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery("query { NopAuthUserEx__active_findPage2{ items{ id, userName } } }");
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        GraphQLResponseBean response = graphQLEngine.executeGraphQL(context);
        assertTrue(!response.hasError());
    }

    @Test
    public void testUpdate() {
        IEntityDao<NopAuthGroup> dao = daoProvider.daoFor(NopAuthGroup.class);
        NopAuthGroup entity = dao.newEntity();
        entity.setName("aa");
        dao.saveEntity(entity);

        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        Map<String, Object> data = Map.of("id", entity.get_id(), "name", "bb");
        request.setData(Map.of("data", data));
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(null, "NopAuthGroup__update", request);
        ApiResponse<?> response = graphQLEngine.executeRpc(context);
        assertTrue(response.isOk());

        entity = dao.getEntityById(entity.get_id());
        assertEquals("bb", entity.getName());
    }

    @Test
    public void testFindTreeEntityPage() {
        IEntityDao<NopAuthDept> dao = daoProvider.daoFor(NopAuthDept.class);
        NopAuthDept entity = dao.newEntity();
        entity.setDeptName("aa01");
        dao.saveEntity(entity);

        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        QueryBean query = new QueryBean();
        query.setLimit(10);
        query.addFilter(FilterBeans.in("deptId", List.of(entity.get_id())));
        request.setData(Map.of("query", query));
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(null, "NopAuthDept__findTreeEntityPage", request);
        ApiResponse<?> response = graphQLEngine.executeRpc(context);
        assertTrue(response.isOk());
        assertEquals(1L, BeanTool.getProperty(response.getData(), "total"));
        assertEquals(1, ((List) BeanTool.getProperty(response.getData(), "items")).size());
        System.out.println(JsonTool.serialize(response,true));
    }

    @Test
    public void testDate(){
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        QueryBean query = new QueryBean();
        query.setLimit(10);
        query.addFilter(FilterBeans.ge("createTime", "2002-01-03"));
        request.setData(Map.of("query", query));
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(null, "NopAuthSite__findPage", request);
        ApiResponse<?> response = graphQLEngine.executeRpc(context);
        assertTrue(response.isOk());
    }
}
