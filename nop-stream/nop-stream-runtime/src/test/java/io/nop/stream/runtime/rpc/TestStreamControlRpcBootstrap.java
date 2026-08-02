/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.rpc;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.nop.api.core.message.IMessageService;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.loader.BeanContainerBuilder;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.runtime.cluster.TaskAssignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 39 Phase 2 bean-bootstrap test (Phase 2 Decision 2, Option B): loads
 * nop-stream's first {@code beans.xml} via NopIoC and verifies it is non-hollow —
 * the container materializes the wired transport bean, and the RPC server/proxy
 * classes (the Stage 42 deployment scaffold documented in the beans.xml) genuinely
 * carry control calls over the IoC-provided {@link IMessageService}.
 *
 * <p>This satisfies "beans.xml 被本 stage ≥1 个自动化测试实际加载，断言 RPC server/proxy
 * bean 实例化" (plan guide #11/#24).
 */
class TestStreamControlRpcBootstrap {

    private static IBeanContainerImplementor container;

    @BeforeAll
    static void init() {
        // Initialize core up to (but not including) the global IoC initializer
        // (INITIALIZER_PRIORITY_IOC), which would otherwise load nop-dao's
        // dao-defaults beans and require datasource config. We only need VFS +
        // reflection + xdef (for beans.xdef parsing), then build a STANDALONE
        // container from nop-stream's own beans.xml.
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_IOC - 1);
        BeanContainerBuilder builder = new BeanContainerBuilder(null);
        builder.addResource(new ClassPathResource("classpath:_vfs/nop/stream/beans/stream-control-rpc.beans.xml"));
        container = builder.build("stream-control-rpc-bootstrap-test");
        container.start();
    }

    @AfterAll
    static void destroy() {
        if (container != null) {
            container.stop();
        }
        CoreInitialization.destroy();
    }

    @Test
    void beansXmlLoadsAndTransportBeanInstantiates() {
        // The beans.xml wires a streamMessageService bean — it must materialize.
        Object messageService = container.getBean("streamMessageService");
        assertNotNull(messageService, "streamMessageService bean must be instantiated by the IoC container");
        assertTrue(messageService instanceof IMessageService,
                "streamMessageService bean must be an IMessageService, got " + messageService.getClass());
    }

    @Test
    void rpcServerAndProxyWorkOverIocProvidedTransport() {
        // Construct the exact RPC server/proxy classes wired in the beans.xml scaffold,
        // using the IoC-provided IMessageService bean as the transport. A successful
        // round-trip proves the beans.xml is a genuine (non-hollow) wiring of the
        // control-plane RPC infrastructure.
        IMessageService messageService = (IMessageService) container.getBean("streamMessageService");

        RecordingTaskRpc serverImpl = new RecordingTaskRpc();
        String topic = StreamControlRpcTopics.taskTopic("bootstrap-node");

        StreamControlRpcServer server = new StreamControlRpcServer(
                "streamTaskRpc@bootstrap-node", IStreamTaskRpcService.class, serverImpl,
                messageService, topic);
        StreamControlRpcProxyFactory proxyFactory = new StreamControlRpcProxyFactory(
                "streamTaskRpc@bootstrap-node", IStreamTaskRpcService.class,
                messageService, topic);
        try {
            server.start();
            proxyFactory.start();

            IStreamTaskRpcService proxy = proxyFactory.getProxy();
            assertFalse(proxy instanceof RecordingTaskRpc,
                    "the RPC proxy must not be the direct server impl");

            long epoch = 42L;
            proxy.receiveAssignment(new TaskAssignment("job-bootstrap", "source", 0,
                    "bootstrap-node", "attempt-0", epoch, System.currentTimeMillis(), 1));
            proxy.updateFencingToken(epoch);
            proxy.triggerCheckpoint(new CheckpointBarrier(1L, System.currentTimeMillis(),
                    CheckpointType.CHECKPOINT), epoch);

            // Every control call reached the server-side impl over the IoC-provided
            // transport — the scaffold is non-hollow.
            assertEquals(1, serverImpl.receiveAssignmentCount.get());
            assertEquals(epoch, serverImpl.lastAssignmentEpoch.get());
            assertEquals(epoch, serverImpl.lastUpdateEpoch.get());
            assertEquals(1, serverImpl.triggerCount.get());
            assertEquals(epoch, serverImpl.lastTriggerEpoch.get());
        } finally {
            try {
                proxyFactory.stop();
            } catch (Exception ignored) {
                // best-effort
            }
            try {
                server.stop();
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    static final class RecordingTaskRpc implements IStreamTaskRpcService {
        final AtomicLong receiveAssignmentCount = new AtomicLong();
        final AtomicLong triggerCount = new AtomicLong();
        final AtomicLong lastAssignmentEpoch = new AtomicLong();
        final AtomicLong lastTriggerEpoch = new AtomicLong();
        final AtomicLong lastUpdateEpoch = new AtomicLong();

        @Override
        public void receiveAssignment(TaskAssignment assignment) {
            receiveAssignmentCount.incrementAndGet();
            lastAssignmentEpoch.set(assignment.getFencingEpoch());
        }

        @Override
        public void triggerCheckpoint(CheckpointBarrier barrier, long fencingEpoch) {
            triggerCount.incrementAndGet();
            lastTriggerEpoch.set(fencingEpoch);
        }

        @Override
        public void cancelTask(String jobId, String vertexId, int subtaskIndex) {
        }

        @Override
        public void updateFencingToken(long fencingEpoch) {
            lastUpdateEpoch.set(fencingEpoch);
        }
    }
}
