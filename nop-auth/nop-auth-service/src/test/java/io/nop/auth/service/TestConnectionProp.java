package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.EnableSnapshot;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.auth.dao.entity.NopAuthResource;
import io.nop.auth.dao.entity.NopAuthSite;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.orm.IOrmTemplate;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;


public class TestConnectionProp extends JunitAutoTestCase {

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    IGraphQLEngine graphQLEngine;

    @EnableSnapshot
    @Test
    public void testConnection() {
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

        run("request.yaml", "response.json5");
        run("request2.yaml", "response2.json5");
    }

    private void run(String requestFileName, String responseFileName) {
        GraphQLRequestBean request = input(requestFileName, GraphQLRequestBean.class);

        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        GraphQLResponseBean result = graphQLEngine.executeGraphQL(context);

        output(responseFileName, result);
    }
}
