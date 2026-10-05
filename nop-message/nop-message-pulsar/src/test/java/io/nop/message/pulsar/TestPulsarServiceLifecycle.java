/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.pulsar;

import io.nop.api.core.exceptions.NopException;
import org.apache.pulsar.client.api.PulsarClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * WI19: {@code PulsarMessageService} runs on the platform {@link io.nop.commons.service.LifeCycleSupport}
 * state machine — {@code init}/{@code destroy} delegate to {@code start}/{@code stop},
 * state transitions are observable via {@code isActive()}, restart after destroy is
 * allowed, and destroy-before-init is a no-op. The {@code createClient()} seam keeps these
 * tests broker-free (mirrors the Kafka createConsumer factory).
 */
class TestPulsarServiceLifecycle {

    /** Overrides client creation so no broker connection is attempted. */
    static class LifecycleTestService extends PulsarMessageService {
        PulsarClient clientForTest = mock(PulsarClient.class);

        @Override
        protected PulsarClient createClient() {
            return clientForTest;
        }
    }

    private LifecycleTestService configuredService() {
        LifecycleTestService service = new LifecycleTestService();
        PulsarClientConfig config = new PulsarClientConfig();
        config.setServiceUrl("pulsar://localhost:6650");
        service.setConfig(config);
        return service;
    }

    @Test
    void initDestroyRunOnLifecycleStateMachineAndSupportRestart() throws Exception {
        LifecycleTestService service = configuredService();
        assertFalse(service.isActive(), "a fresh service must not be active");

        service.init();
        assertTrue(service.isActive(), "init must go through LifeCycleSupport.start()");

        service.destroy();
        assertFalse(service.isActive(), "destroy must go through LifeCycleSupport.stop()");
        verify(service.clientForTest).close();

        // allowRestart defaults to true: re-init after destroy is supported.
        service.init();
        assertTrue(service.isActive());
        service.destroy();
        assertFalse(service.isActive());
    }

    @Test
    void initFailureLeavesServiceInactive() {
        PulsarMessageService service = new PulsarMessageService();
        service.setConfig(new PulsarClientConfig()); // serviceUrl missing
        assertThrows(NopException.class, service::init);
        assertFalse(service.isActive(), "a failed start must not leave the service active");
    }

    @Test
    void destroyWithoutInitIsNoOp() {
        LifecycleTestService service = new LifecycleTestService();
        service.destroy(); // ServiceStatus.CREATED disallows stop — must not throw
        assertFalse(service.isActive());
        verifyNoInteractions(service.clientForTest);
    }
}
