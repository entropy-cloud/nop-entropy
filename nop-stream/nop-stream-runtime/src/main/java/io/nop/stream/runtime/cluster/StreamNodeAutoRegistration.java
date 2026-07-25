/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.cluster;

import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.annotations.core.Internal;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.naming.INamingService;

/**
 * Registers a nop-stream node with the platform {@link INamingService} so that it
 * becomes discoverable via platform discovery.
 *
 * <p>This bean follows the platform bean lifecycle convention
 * ({@code @PostConstruct} register / {@code @PreDestroy} unregister), the same
 * pattern used by nop-job and nop-graphql-grpc. It is <strong>not</strong> embedded
 * inside {@code TaskManager.start()} — the two concerns (runtime lease vs platform
 * discovery) are kept separate so they can evolve independently.
 *
 * <p><strong>ServiceInstance field mapping</strong> ({@link ServiceInstance} has no
 * {@code capacity} field):
 * <ul>
 *   <li>{@code instanceId} = nodeId</li>
 *   <li>{@code addr} + {@code port} = parsed from endpoint (host:port)</li>
 *   <li>{@code weight} = capacity</li>
 *   <li>{@code metadata["capacity"]} = capacity (redundant for explicit read)</li>
 *   <li>{@code serviceName} = {@value #SERVICE_NAME}</li>
 * </ul>
 *
 * <p>Registration uses {@link INamingService#registerInstance} (the write-capable
 * interface), <strong>not</strong> {@link io.nop.cluster.discovery.IDiscoveryClient}
 * (read-only). Registration failure is propagated as an exception, never silently
 * swallowed — the node must be discoverable or the system fails fast.
 *
 * <p>This is a <strong>single-direction registration</strong> (nop-stream → platform
 * discovery). nop-stream does not consume discovery reads for assignment or failure
 * detection; {@link ClusterRegistry} remains the sole runtime source of truth for
 * those concerns. The two views should be consistent during the node's lifetime
 * (registered node = lease-active node). Full convergence (ClusterRegistry vs
 * platform discovery) is deferred to Stage 41 decision point D7.
 */
@Internal
public class StreamNodeAutoRegistration {

    static final Logger LOG = LoggerFactory.getLogger(StreamNodeAutoRegistration.class);

    public static final String SERVICE_NAME = "nop-stream";
    public static final String META_CAPACITY = "capacity";

    private final INamingService namingService;
    private final String nodeId;
    private final String endpoint;
    private final int capacity;

    private ServiceInstance instance;

    public StreamNodeAutoRegistration(INamingService namingService, String nodeId, String endpoint, int capacity) {
        this.namingService = namingService;
        this.nodeId = nodeId;
        this.endpoint = endpoint;
        this.capacity = capacity;
    }

    @PostConstruct
    public void start() {
        ServiceInstance svc = new ServiceInstance();
        svc.setInstanceId(nodeId);
        svc.setServiceName(SERVICE_NAME);
        svc.setHealthy(true);
        svc.setEnabled(true);
        svc.setWeight(capacity);

        // Parse endpoint "host:port" into addr + port
        Endpoint parsed = Endpoint.parse(endpoint);
        svc.setAddr(parsed.host);
        svc.setPort(parsed.port);

        Map<String, String> metadata = new HashMap<>();
        metadata.put(META_CAPACITY, String.valueOf(capacity));
        svc.setMetadata(metadata);

        this.instance = svc;

        // Registration failure must propagate — never silently swallow.
        // If the node cannot be registered with discovery, downstream consumers
        // relying on discovery would silently miss it.
        namingService.registerInstance(svc);

        LOG.info("Registered nop-stream node {} (endpoint={}, capacity={}) with platform discovery service {}",
                nodeId, endpoint, capacity, SERVICE_NAME);
    }

    @PreDestroy
    public void stop() {
        if (instance != null) {
            try {
                namingService.unregisterInstance(instance);
                LOG.info("Unregistered nop-stream node {} from platform discovery", nodeId);
            } catch (Exception e) {
                LOG.warn("Failed to unregister nop-stream node {} from platform discovery", nodeId, e);
            }
            instance = null;
        }
    }

    /**
     * @return the {@link ServiceInstance} that was registered, or null before {@link #start()} / after {@link #stop()}.
     */
    public ServiceInstance getServiceInstance() {
        return instance;
    }

    public String getNodeId() {
        return nodeId;
    }

    // ==================== Inner ====================

    static class Endpoint {
        final String host;
        final int port;

        private Endpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }

        static Endpoint parse(String endpoint) {
            if (endpoint == null || endpoint.isEmpty()) {
                return new Endpoint("localhost", 0);
            }
            int colon = endpoint.lastIndexOf(':');
            if (colon > 0) {
                String host = endpoint.substring(0, colon);
                int port;
                try {
                    port = Integer.parseInt(endpoint.substring(colon + 1));
                } catch (NumberFormatException e) {
                    port = 0;
                }
                return new Endpoint(host, port);
            }
            return new Endpoint(endpoint, 0);
        }
    }
}
