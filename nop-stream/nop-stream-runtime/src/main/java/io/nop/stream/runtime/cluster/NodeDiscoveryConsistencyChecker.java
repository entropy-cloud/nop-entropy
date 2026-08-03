/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.cluster;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.cluster.discovery.IDiscoveryClient;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DISCOVERY_ONLY;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_REGISTRY_ONLY;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_DISCOVERY_DRIFT;

/**
 * Stage 41 D7 (Option B — coexistence): consumes the platform discovery
 * <strong>read</strong> direction ({@link IDiscoveryClient#getInstances}) and
 * cross-checks it against the {@link ClusterRegistry} runtime source of truth.
 *
 * <p>This closes the G51/D7 「双向一致性契约」: a node registered with discovery
 * (via {@link StreamNodeAutoRegistration}) should be lease-active in the
 * {@link ClusterRegistry}, and vice-versa. The two views share the same JDBC
 * backing store but live in different tables ({@code nop_sys_service_instance}
 * vs {@code nop_stream_node}) and are updated by independent code paths, so they
 * are <strong>eventually consistent, non-transactional</strong>. Persistent
 * divergence indicates a missed registration, a stale lease, or a discovery
 * backend outage.
 *
 * <p><strong>Why this is the read-direction consumer</strong>: the write
 * direction (nop-stream → discovery) is already wired by
 * {@link StreamNodeAutoRegistration}. Without a reader, the discovery read path
 * ({@link IDiscoveryClient}) would be a dead contract surface. This checker is
 * the genuine, non-hollow consumer of that surface — it actually calls
 * {@code getInstances} at runtime and reports the result.
 *
 * <p><strong>Why it is optional</strong>: the {@link ClusterRegistry} remains
 * the sole runtime source of truth for task assignment / fencing / lease. This
 * checker is a diagnostic / observability tool (drift detection), not a control-
 * path component. It is constructed only when a deployment wires both a
 * discovery client and a registry; the backward-compatible no-discovery path
 * (e.g. embedded execution without a naming service) never builds it.
 *
 * <p><strong>No silent no-op</strong> (plan guide #24): drift detection methods
 * never swallow divergence. {@link #check()} returns a report describing any
 * drift; {@link #assertConsistent()} throws {@link StreamException} on drift so
 * callers using it as a health gate fail loud.
 */
@Internal
public class NodeDiscoveryConsistencyChecker {

    static final Logger LOG = LoggerFactory.getLogger(NodeDiscoveryConsistencyChecker.class);

    private final IDiscoveryClient discoveryClient;
    private final ClusterRegistry clusterRegistry;
    private final String serviceName;

    /**
     * @param discoveryClient read-only platform discovery (the G51 read direction)
     * @param clusterRegistry runtime source of truth for active nodes
     * @param serviceName     the service name nop-stream registers under
     *                        ({@link StreamNodeAutoRegistration#SERVICE_NAME})
     */
    public NodeDiscoveryConsistencyChecker(IDiscoveryClient discoveryClient,
                                           ClusterRegistry clusterRegistry,
                                           String serviceName) {
        this.discoveryClient = discoveryClient;
        this.clusterRegistry = clusterRegistry;
        this.serviceName = serviceName;
    }

    public NodeDiscoveryConsistencyChecker(IDiscoveryClient discoveryClient,
                                           ClusterRegistry clusterRegistry) {
        this(discoveryClient, clusterRegistry, StreamNodeAutoRegistration.SERVICE_NAME);
    }

    /**
     * Reads both views and reports divergence. Does not throw on drift — callers
     * that need fail-loud semantics use {@link #assertConsistent()}.
     *
     * <p>This is the genuine read-direction invocation: {@code discoveryClient
     * .getInstances(serviceName)} is called here, proving the discovery read
     * surface is consumed at runtime (not a dead contract).
     *
     * @return a consistency report; never null
     */
    public ConsistencyReport check() {
        List<ServiceInstance> discoveryInstances = discoveryClient.getInstances(serviceName);
        Set<String> discoveryIds = new HashSet<>();
        for (ServiceInstance svc : discoveryInstances) {
            discoveryIds.add(svc.getInstanceId());
        }

        Set<String> registryIds = new HashSet<>();
        List<NodeInfo> activeNodes = clusterRegistry.getActiveNodes();
        for (NodeInfo node : activeNodes) {
            registryIds.add(node.getNodeId());
        }

        Set<String> onlyInDiscovery = new HashSet<>(discoveryIds);
        onlyInDiscovery.removeAll(registryIds);

        Set<String> onlyInRegistry = new HashSet<>(registryIds);
        onlyInRegistry.removeAll(discoveryIds);

        boolean consistent = onlyInDiscovery.isEmpty() && onlyInRegistry.isEmpty();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Discovery/registry consistency check: serviceName={}, discovery={}, registry={}, consistent={}",
                    serviceName, discoveryIds, registryIds, consistent);
        }

        return new ConsistencyReport(consistent, discoveryIds, registryIds, onlyInDiscovery, onlyInRegistry);
    }

    /**
     * Fail-loud variant for callers using the checker as a health gate. Throws
     * {@link StreamException} (ERR_STREAM_DISCOVERY_DRIFT) when the two views
     * diverge — never silently returns while drift exists (plan guide #24).
     */
    public void assertConsistent() {
        ConsistencyReport report = check();
        if (!report.isConsistent()) {
            throw new StreamException(ERR_STREAM_DISCOVERY_DRIFT)
                    .param(ARG_DISCOVERY_ONLY, report.getOnlyInDiscovery())
                    .param(ARG_REGISTRY_ONLY, report.getOnlyInRegistry());
        }
    }

    // ==================== Report ====================

    /**
     * Immutable snapshot of one consistency check. The two views are eventually
     * consistent (same DB, different tables, non-transactional), so a fresh
     * check may legitimately differ from an earlier one while registration /
     * lease-renewal / cleanup propagates.
     */
    public static final class ConsistencyReport {
        private final boolean consistent;
        private final Set<String> discoveryInstanceIds;
        private final Set<String> registryNodeIds;
        private final Set<String> onlyInDiscovery;
        private final Set<String> onlyInRegistry;

        ConsistencyReport(boolean consistent,
                          Set<String> discoveryInstanceIds,
                          Set<String> registryNodeIds,
                          Set<String> onlyInDiscovery,
                          Set<String> onlyInRegistry) {
            this.consistent = consistent;
            this.discoveryInstanceIds = discoveryInstanceIds;
            this.registryNodeIds = registryNodeIds;
            this.onlyInDiscovery = onlyInDiscovery;
            this.onlyInRegistry = onlyInRegistry;
        }

        public boolean isConsistent() {
            return consistent;
        }

        public Set<String> getDiscoveryInstanceIds() {
            return discoveryInstanceIds;
        }

        public Set<String> getRegistryNodeIds() {
            return registryNodeIds;
        }

        public Set<String> getOnlyInDiscovery() {
            return onlyInDiscovery;
        }

        public Set<String> getOnlyInRegistry() {
            return onlyInRegistry;
        }

        @Override
        public String toString() {
            return "ConsistencyReport{consistent=" + consistent
                    + ", discovery=" + discoveryInstanceIds
                    + ", registry=" + registryNodeIds
                    + ", onlyInDiscovery=" + onlyInDiscovery
                    + ", onlyInRegistry=" + onlyInRegistry + "}";
        }
    }
}
