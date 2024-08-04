/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.auth.dao.entity.NopAuthResource;
import io.nop.auth.dao.entity.NopAuthSite;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.graphql.core.utils.GraphQLArgsHelper;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;


public class TestConnectionProp extends JunitAutoTestCase {

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    IGraphQLEngine graphQLEngine;


    private void prepareData() {
        ormTemplate.runInSession(() -> {
            NopAuthSite site = (NopAuthSite) ormTemplate.newEntity(NopAuthSite.class.getName());
            site.setSiteId("test");
            site.setDisplayName("Test");
            site.setOrderNo(2);
            site.setStatus(0);

            NopAuthResource resource = (NopAuthResource) ormTemplate.newEntity(NopAuthResource.class.getName());
            resource.setResourceId("test1");
            resource.setDisplayName("RES1");
            resource.setOrderNo(2);
            resource.setResourceType("TOPM");
            site.getResources().add(resource);

            NopAuthSite site2 = (NopAuthSite) ormTemplate.newEntity(NopAuthSite.class.getName());
            site2.setSiteId("test2");
            site2.setDisplayName("Test2");
            site2.setOrderNo(3);
            site2.setStatus(0);

            NopAuthResource resource2 = (NopAuthResource) ormTemplate.newEntity(NopAuthResource.class.getName());
            resource2.setResourceId("test2");
            resource2.setDisplayName("RES2");
            resource2.setOrderNo(3);
            resource2.setResourceType("TOPM");
            site2.getResources().add(resource2);

            ormTemplate.save(site);
            ormTemplate.save(site2);
        });

    }

    @EnableSnapshot
    @Test
    public void testConnection() {
        prepareData();
        run("request.yaml", "response.json5");
        run("request2.yaml", "response2.json5");
        run("request3.yaml", "response3.json5");
    }

    private void run(String requestFileName, String responseFileName) {
        GraphQLRequestBean request = input(requestFileName, GraphQLRequestBean.class);

        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        GraphQLResponseBean result = graphQLEngine.executeGraphQL(context);

        output(responseFileName, result);
    }

    @EnableSnapshot
    @Test
    public void testRestFilter() {
        prepareData();
        ApiRequest<Map<String, Object>> request = request("request.yaml", Map.class);
        GraphQLArgsHelper.normalizeSubArgs(request.getSelection(), request.getData());

        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(null, "NopAuthSite__get", request);
        ApiResponse<?> result = graphQLEngine.executeRpc(context);

        output("response.yaml", result);
    }

    @EnableSnapshot
    @Test
    public void testTransFilter() {
        prepareData();
        run("request.yaml", "response.yaml");
    }

    @EnableSnapshot
    @Test
    public void testFindList() {
        prepareData();

        ApiRequest<Map<String, Object>> request = request("request.yaml", Map.class);
        GraphQLArgsHelper.normalizeSubArgs(request.getSelection(), request.getData());

        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(null, "NopAuthSite__get", request);
        ApiResponse<?> result = graphQLEngine.executeRpc(context);

        output("response.yaml", result);
    }
}
