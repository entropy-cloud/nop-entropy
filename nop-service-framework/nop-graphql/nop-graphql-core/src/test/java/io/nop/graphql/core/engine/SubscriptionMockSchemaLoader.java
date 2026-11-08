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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
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
        /**
         * 时间确定化（plan 2513-WI7）：continuous 流的测试侧放行闩。测试在订阅前置入非 null 闩，
         * 生产者提交首条事件后挂起，测试确认取消/关闭完成后再放行——放行后的提交发生在
         * 订阅已取消之后，事件被 Flow 协议确定性丢弃。未置闩时（其他测试）直接连续提交。
         */
        static volatile CountDownLatch continuousGate;
        /** 生产者提交完闸门后剩余事件并 close 后 countDown，作为「流已终结」的确定性证据。 */
        static volatile CountDownLatch continuousProducerDone;
        /** onTestEvent 生产者线程终结证据。 */
        static volatile CountDownLatch testEventDone;

        /**
         * 时间确定化（plan 2513-WI7）：SubmissionPublisher 只向已接入的订阅者投递事件——
         * 提交前先等「订阅者已接入」这一确定性事实（条件等待，超时只作挂死保护），
         * 避免生产者抢先 submit 导致首条事件被静默丢弃。
         */
        private static void awaitSubscriber(SubmissionPublisher<?> publisher)
                throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (publisher.getNumberOfSubscribers() == 0) {
                if (System.nanoTime() > deadline)
                    throw new IllegalStateException("no subscriber attached to mock publisher within 10s");
                LockSupport.parkNanos(1_000_000L);
            }
        }

        @BizSubscription
        @GraphQLReturn(bizObjName = "TestEvent")
        public Flow.Publisher<TestEvent> onTestEvent(@Name("filter") String filter) {
            SubmissionPublisher<TestEvent> publisher = new SubmissionPublisher<>();
            
            // 时间确定化（plan 2513-WI7）：有限事件流一次性提交后 close，事件推进由
            // 订阅方 request(1) 拉动，不依赖真实时间流逝。
            new Thread(() -> {
                try {
                    awaitSubscriber(publisher);
                    for (int i = 0; i < 3; i++) {
                        TestEvent event = new TestEvent();
                        event.setId("event_" + i);
                        event.setMessage("Test message " + i + (filter != null ? " filter:" + filter : ""));
                        publisher.submit(event);
                    }
                    publisher.close();
                } catch (Throwable e) {
                    publisher.closeExceptionally(e);
                } finally {
                    testEventDone.countDown();
                }
            }).start();
            
            return publisher;
        }

        @BizSubscription
        @GraphQLReturn(bizObjName = "TestEvent")
        public Flow.Publisher<TestEvent> onContinuousEvent() {
            SubmissionPublisher<TestEvent> publisher = new SubmissionPublisher<>();
            
            // 时间确定化（plan 2513-WI7）：continuous 流改为测试侧闸门控制的有限流——
            // 首条事件提交后（证明订阅已建立）挂起等待测试放行，放行后提交其余事件并 close。
            // 不再依赖真实时间流逝产生事件节奏。
            new Thread(() -> {
                try {
                    awaitSubscriber(publisher);
                    for (int i = 0; i < 3; i++) {
                        TestEvent event = new TestEvent();
                        event.setId("continuous_" + i);
                        event.setMessage("Continuous event " + i);
                        publisher.submit(event);
                        if (i == 0 && continuousGate != null) {
                            continuousGate.await();
                        }
                    }
                    publisher.close();
                } catch (Throwable e) {
                    publisher.closeExceptionally(e);
                } finally {
                    if (continuousProducerDone != null)
                        continuousProducerDone.countDown();
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
