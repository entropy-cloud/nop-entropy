/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.BizSubscription;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.ast.GraphQLArgumentDefinition;
import io.nop.graphql.core.ast.GraphQLDefinition;
import io.nop.graphql.core.ast.GraphQLDocument;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.fetcher.BeanPropertyFetcher;
import io.nop.graphql.core.parse.GraphQLDocumentHelper;
import io.nop.graphql.core.reflection.GraphQLBizModels;
import io.nop.graphql.core.schema.IGraphQLSchemaLoader;
import io.nop.graphql.core.schema.TypeRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.stream.Collectors;

public class SubscriptionMockSchemaLoader implements IGraphQLSchemaLoader {
    private Map<String, GraphQLObjectDefinition> defs;
    private TypeRegistry typeRegistry = new TypeRegistry();
    private GraphQLBizModels bizModels = new GraphQLBizModels();

    public SubscriptionMockSchemaLoader() {
        IResource resource = new ClassPathResource("classpath:io/nop/graphql/core/engine/subscription-test.graphql");
        defs = GraphQLDocumentHelper.parseObjectDefinitions(resource);

        List<Object> beans = Arrays.asList(new TestSubscriptionBizModel());
        bizModels.build(typeRegistry, beans);

        for (GraphQLObjectDefinition objDef : defs.values()) {
            objDef.init();
            for (GraphQLFieldDefinition fieldDef : objDef.getFields()) {
                if (fieldDef.getFetcher() == null) {
                    fieldDef.setFetcher(BeanPropertyFetcher.INSTANCE);
                }
            }
        }
    }

    @Override
    public GraphQLFieldDefinition getOperationDefinition(GraphQLOperationType opType, String name) {
        GraphQLFieldDefinition field = bizModels.getOperationDefinition(opType, name);
        if (field != null) {
            return field;
        }
        
        if (opType == GraphQLOperationType.subscription) {
            GraphQLObjectDefinition subscriptionDef = defs.get("Subscription");
            if (subscriptionDef != null) {
                return subscriptionDef.getField(name);
            }
        }
        return null;
    }

    @Override
    public GraphQLObjectDefinition getObjectTypeDefinition(String objName) {
        return defs.get(objName);
    }

    @Override
    public GraphQLObjectDefinition resolveTypeDefinition(GraphQLType type) {
        return defs.get(type.getNamedTypeName());
    }

    @Override
    public List<GraphQLFieldDefinition> getOperationDefinitions(GraphQLOperationType opType) {
        return Collections.emptyList();
    }

    @Override
    public GraphQLTypeDefinition getTypeDefinition(String objName) {
        return getObjectTypeDefinition(objName);
    }

    @Override
    public FieldSelectionBean getFragmentDefinition(String objName, String fragmentName) {
        return null;
    }

    @Override
    public Collection<GraphQLTypeDefinition> getTypeDefinitions() {
        return new ArrayList<>(defs.values());
    }

    @Override
    public GraphQLDocument getGraphQLDocument() {
        GraphQLDocument doc = new GraphQLDocument();
        List<GraphQLDefinition> definitions = defs.values().stream()
                .map(GraphQLObjectDefinition::deepClone)
                .collect(Collectors.toList());
        doc.setDefinitions(definitions);
        return doc;
    }

    @Override
    public Set<String> getBizObjNames() {
        return this.defs.keySet();
    }

    @Override
    public Map<String, GraphQLFieldDefinition> getBizOperationDefinitions(String bizObjName) {
        return Collections.emptyMap();
    }

    @BizModel("TestSubscription")
    public static class TestSubscriptionBizModel {

        @BizSubscription
        @GraphQLReturn(bizObjName = "TestEvent")
        public Flow.Publisher<TestEvent> onTestEvent(@Name("filter") String filter) {
            SubmissionPublisher<TestEvent> publisher = new SubmissionPublisher<>();
            
            new Thread(() -> {
                try {
                    for (int i = 0; i < 3; i++) {
                        TestEvent event = new TestEvent();
                        event.setId("event_" + i);
                        event.setMessage("Test message " + i + (filter != null ? " filter:" + filter : ""));
                        publisher.submit(event);
                        Thread.sleep(50);
                    }
                    publisher.close();
                } catch (InterruptedException e) {
                    publisher.closeExceptionally(e);
                }
            }).start();
            
            return publisher;
        }

        @BizSubscription
        @GraphQLReturn(bizObjName = "TestEvent")
        public Flow.Publisher<TestEvent> onContinuousEvent() {
            SubmissionPublisher<TestEvent> publisher = new SubmissionPublisher<>();
            
            new Thread(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        TestEvent event = new TestEvent();
                        event.setId("continuous_" + i);
                        event.setMessage("Continuous event " + i);
                        publisher.submit(event);
                        Thread.sleep(10);
                    }
                    publisher.close();
                } catch (InterruptedException e) {
                    publisher.closeExceptionally(e);
                }
            }).start();
            
            return publisher;
        }

        @BizQuery
        @GraphQLReturn(bizObjName = "TestEvent")
        public TestEvent get(@Name("id") String id) {
            TestEvent event = new TestEvent();
            event.setId(id);
            event.setMessage("Queried event");
            return event;
        }

        @BizMutation
        @GraphQLReturn(bizObjName = "TestEvent")
        public TestEvent update(@Name("id") String id,
                               @Name("message") String message) {
            TestEvent event = new TestEvent();
            event.setId(id);
            event.setMessage(message);
            return event;
        }
    }

    @DataBean
    public static class TestEvent {
        private String id;
        private String message;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
