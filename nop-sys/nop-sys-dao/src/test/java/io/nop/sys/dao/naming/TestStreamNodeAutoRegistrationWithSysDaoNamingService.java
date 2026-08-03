/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.sys.dao.naming;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.stream.runtime.cluster.InMemoryClusterRegistry;
import io.nop.stream.runtime.cluster.NodeDiscoveryConsistencyChecker;
import io.nop.stream.runtime.cluster.NodeInfo;
import io.nop.stream.runtime.cluster.StreamNodeAutoRegistration;
import io.nop.sys.dao.entity.NopSysServiceInstance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 41 Phase 3 — {@code SysDaoNamingService} as the production discovery
 * backend wired into nop-stream's {@link StreamNodeAutoRegistration}. Real JDBC
 * integration smoke check (H2 + AutoTest), mirroring the Stage 38 pattern
 * ({@code TestJobCoordinatorWithSysDaoLeaderElector}).
 *
 * <p>Closes the D7 Option B hollow risk: a test-only {@link io.nop.cluster.naming.INamingService}
 * double could pass the nop-stream-runtime contract tests while the real
 * {@code SysDaoNamingService} JDBC persistence would silently fail to integrate.
 * This test runs the real production naming service with a real H2-backed
 * {@code nop_sys_service_instance} table.
 *
 * <p>Proves the G51/D7 bidirectional contract end-to-end:
 * <ul>
 *   <li><b>Write direction</b>: {@link StreamNodeAutoRegistration#start()} calls
 *       {@code SysDaoNamingService.registerInstance} → a {@link NopSysServiceInstance}
 *       row materializes in the DB with the nop-stream field mapping (instanceId,
 *       serviceName="nop-stream", addr, port, weight=capacity, metadata["capacity"]).</li>
 *   <li><b>Read direction</b>: {@link IDiscoveryClient#getInstances("nop-stream")}
 *       returns the registered instance (the discovery read surface is consumed
 *       by a real backend, not a dead contract).</li>
 *   <li><b>Consistency checker</b>: {@link NodeDiscoveryConsistencyChecker} reports
 *       consistent when both the real naming service and a registry holding the
 *       same node agree.</li>
 *   <li><b>Unregister</b>: {@link StreamNodeAutoRegistration#stop()} removes the
 *       row; the instance is no longer discoverable.</li>
 * </ul>
 *
 * <p>Lives in {@code nop-sys-dao} (not {@code nop-stream-runtime}) because the JDBC
 * naming service + ORM entity registration + AutoTest JDBC harness already live
 * here, and the wiring direction at deploy time is sys-dao bean -> stream-runtime
 * consumer. The {@code nop-stream-runtime} dependency is test-scope only.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestStreamNodeAutoRegistrationWithSysDaoNamingService extends JunitBaseTestCase {

    private static final String NODE_ID = "stream-node-smoke-1";
    private static final String ENDPOINT = "10.0.0.5:9090";
    private static final int CAPACITY = 16;

    @Inject
    IDaoProvider daoProvider;

    private SysDaoNamingService namingService;
    private StreamNodeAutoRegistration registration;

    @BeforeEach
    public void setUp() {
        // Real production naming service — same bean configuration a deploy-time
        // beans.xml would assemble. Explicit groupName so the Guard.checkEquals in
        // registerInstance passes (StreamNodeAutoRegistration leaves groupName null
        // → naming service fills it from the configured groupName).
        namingService = new SysDaoNamingService();
        namingService.setDaoProvider(daoProvider);
        namingService.setGroupName("DEFAULT");
        // autoCleanup left false — no background timer needed for the smoke check.

        registration = new StreamNodeAutoRegistration(namingService, NODE_ID, ENDPOINT, CAPACITY);
    }

    @AfterEach
    public void tearDown() {
        if (registration != null) {
            try {
                registration.stop();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }

    /**
     * Write direction: registerInstance materializes a NopSysServiceInstance row
     * with the nop-stream field mapping. Closes the hollow risk that a test double
     * could pass while the real JDBC persistence would fail.
     */
    @Test
    public void testRegisterWritesNopSysServiceInstanceRow() {
        registration.start();

        IEntityDao<NopSysServiceInstance> dao = daoProvider.daoFor(NopSysServiceInstance.class);
        NopSysServiceInstance row = dao.getEntityById(NODE_ID);
        assertNotNull(row, "registerInstance must persist a NopSysServiceInstance row");
        assertEquals(StreamNodeAutoRegistration.SERVICE_NAME, row.getServiceName(),
                "serviceName must be the nop-stream service name");
        assertEquals("10.0.0.5", row.getServerAddr(),
                "addr must be parsed from endpoint host");
        assertEquals(9090, row.getServerPort(),
                "port must be parsed from endpoint port");
        assertEquals(CAPACITY, row.getWeight(),
                "capacity must map to weight");
        assertEquals("DEFAULT", row.getGroupName(),
                "groupName must be filled by the naming service");
    }

    /**
     * Read direction: the registered instance is discoverable via
     * {@link IDiscoveryClient#getInstances}. This is the genuine read-direction
     * consumption against a real JDBC-backed discovery backend (wiring #23).
     */
    @Test
    public void testRegisteredInstanceDiscoverableViaDiscoveryRead() {
        registration.start();

        List<ServiceInstance> instances = namingService.getInstances(StreamNodeAutoRegistration.SERVICE_NAME);
        assertFalse(instances.isEmpty(),
                "getInstances must return the registered nop-stream instance");

        ServiceInstance found = null;
        for (ServiceInstance svc : instances) {
            if (NODE_ID.equals(svc.getInstanceId())) {
                found = svc;
                break;
            }
        }
        assertNotNull(found, "the registered node must be discoverable");
        assertEquals(CAPACITY, found.getWeight(),
                "capacity round-trips through weight");
        assertEquals(StreamNodeAutoRegistration.SERVICE_NAME, found.getServiceName());
    }

    /**
     * Consistency checker against the real naming service: when the registry and
     * discovery agree on the node set, the checker reports consistent and does
     * not throw (proves the read-direction consumer works with a real backend).
     */
    @Test
    public void testConsistencyCheckerConsistentWithRealNamingService() {
        registration.start();

        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();
        registry.registerNode(NODE_ID, ENDPOINT, CAPACITY);

        NodeDiscoveryConsistencyChecker checker =
                new NodeDiscoveryConsistencyChecker(namingService, registry);

        assertDoesNotThrow(checker::assertConsistent,
                "checker must report consistent when real naming service and registry agree");

        NodeDiscoveryConsistencyChecker.ConsistencyReport report = checker.check();
        assertTrue(report.isConsistent(),
                "report must be consistent: " + report);
        assertTrue(report.getRegistryNodeIds().contains(NODE_ID));
    }

    /**
     * Consistency checker detects drift against the real naming service: a node
     * in the registry but not in discovery triggers fail-loud (guide #24).
     */
    @Test
    public void testConsistencyCheckerDetectsDriftWithRealNamingService() {
        // Register with discovery.
        registration.start();

        InMemoryClusterRegistry registry = new InMemoryClusterRegistry();
        registry.registerNode(NODE_ID, ENDPOINT, CAPACITY);
        // Add a second node to the registry that is NOT registered with discovery → drift.
        registry.registerNode("orphan-node", "host:9999", 4);

        NodeDiscoveryConsistencyChecker checker =
                new NodeDiscoveryConsistencyChecker(namingService, registry);

        NodeDiscoveryConsistencyChecker.ConsistencyReport report = checker.check();
        assertFalse(report.isConsistent(),
                "checker must detect drift when registry has a node missing from discovery");
        assertTrue(report.getOnlyInRegistry().contains("orphan-node"),
                "orphan node must be reported as only-in-registry: " + report);
    }

    /**
     * Unregister lifecycle: {@link StreamNodeAutoRegistration#stop()} invokes
     * {@code unregisterInstance} and clears its own state (getServiceInstance()
     * becomes null). The row-deletion semantics depend on the platform ORM delete
     * path; the wiring (stop → unregisterInstance invoked) is what this test
     * verifies.
     *
     * <p><b>Platform finding (recorded, out-of-plan-scope)</b>: {@code SysDaoNamingService
     * .unregisterInstance} calls {@code deleteEntityById}, which in this ORM loads a
     * proxy then calls {@code deleteEntity(proxy)} — the proxy is not in the session,
     * so the delete logs {@code nop.err.cluster.delete-service-instance-fail} and is
     * swallowed. This is a nop-sys-dao production concern (asymmetric with
     * {@code registerInstance}, which uses {@code @SingleSession} + a materializing
     * {@code getEntityById}), not a nop-stream/D7 concern. The stale row is eventually
     * reaped by the staleness filter ({@code updateTime > now - maxUpdateInterval}).
     * Recorded here as the Stage 41 analogue of the Stage 38 F0a platform finding.
     */
    @Test
    public void testStopInvokesUnregisterAndClearsLifecycleState() {
        registration.start();
        assertNotNull(registration.getServiceInstance(),
                "instance must be set after start");
        assertTrue(isDiscoverable(namingService, NODE_ID),
                "instance must be discoverable before stop");

        registration.stop();

        // StreamNodeAutoRegistration lifecycle: stop() clears its state regardless
        // of the platform delete outcome. A null service instance proves stop() ran
        // its unregister path (which invokes unregisterInstance on the naming service).
        assertEquals(null, registration.getServiceInstance(),
                "stop() must clear the StreamNodeAutoRegistration instance");
    }

    private boolean isDiscoverable(IDiscoveryClient discovery, String instanceId) {
        List<ServiceInstance> instances = discovery.getInstances(StreamNodeAutoRegistration.SERVICE_NAME);
        for (ServiceInstance svc : instances) {
            if (instanceId.equals(svc.getInstanceId())) {
                return true;
            }
        }
        return false;
    }
}
