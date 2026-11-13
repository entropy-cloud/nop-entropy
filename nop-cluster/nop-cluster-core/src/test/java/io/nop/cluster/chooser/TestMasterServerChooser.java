/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.chooser;

import io.nop.api.core.exceptions.NopException;
import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.elector.ILeaderChangeListener;
import io.nop.cluster.elector.ILeaderObserver;
import io.nop.cluster.elector.LeaderEpoch;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.nop.cluster.ClusterErrors.ARG_SERVICE_NAME;
import static io.nop.cluster.ClusterErrors.ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 4.2-MA-09（plan 2559 裁定 D）：MasterServerChooser 两处 return null 改类型化错误——
 * chooser 无实例即语义失败（空 leaderId / leaderId 不在候选集），抛
 * ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE + ARG_SERVICE_NAME，不返回 null 让下游猜。
 */
public class TestMasterServerChooser {

    static class StubLeaderObserver implements ILeaderObserver {
        String leaderId;

        @Override
        public String getLeaderId() {
            return leaderId;
        }

        @Override
        public LeaderEpoch getLeaderEpoch() {
            return null;
        }

        @Override
        public AutoCloseable addLeaderChangeListener(ILeaderChangeListener listener) {
            return null;
        }
    }

    static class StubDiscoveryClient implements IDiscoveryClient {
        @Override
        public List<ServiceInstance> getInstances(String serviceName) {
            return Collections.emptyList();
        }

        @Override
        public List<String> getServices() {
            return Collections.emptyList();
        }
    }

    private static ServiceInstance instance(String id) {
        ServiceInstance instance = new ServiceInstance();
        instance.setInstanceId(id);
        return instance;
    }

    @Test
    public void testEmptyLeaderIdFailsTyped() {
        StubLeaderObserver observer = new StubLeaderObserver();
        observer.leaderId = null;
        MasterServerChooser<String> chooser = new MasterServerChooser<>(new StubDiscoveryClient(), observer);
        chooser.setServiceName("svc");

        NopException err = assertThrows(NopException.class,
                () -> chooser.chooseFromCandidates(Arrays.asList(instance("i1"), instance("i2")), "req"),
                "empty leaderId must fail typed, not return null");
        assertEquals(ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE.getErrorCode(), err.getErrorCode());
        assertEquals("svc", err.getParam(ARG_SERVICE_NAME));
    }

    @Test
    public void testLeaderNotInCandidatesFailsTyped() {
        StubLeaderObserver observer = new StubLeaderObserver();
        observer.leaderId = "leader-x";
        MasterServerChooser<String> chooser = new MasterServerChooser<>(new StubDiscoveryClient(), observer);
        chooser.setServiceName("svc");

        NopException err = assertThrows(NopException.class,
                () -> chooser.chooseFromCandidates(Collections.singletonList(instance("i1")), "req"),
                "leaderId not among candidates must fail typed, not return null");
        assertEquals(ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE.getErrorCode(), err.getErrorCode());
    }

    @Test
    public void testLeaderMatchReturnsInstance() {
        StubLeaderObserver observer = new StubLeaderObserver();
        observer.leaderId = "leader-x";
        MasterServerChooser<String> chooser = new MasterServerChooser<>(new StubDiscoveryClient(), observer);

        ServiceInstance matched = instance("leader-x");
        assertSame(matched, chooser.chooseFromCandidates(Arrays.asList(instance("i1"), matched), "req"));
    }
}
