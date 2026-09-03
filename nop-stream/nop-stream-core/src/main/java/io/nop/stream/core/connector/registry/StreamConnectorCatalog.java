/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.registry;

import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import java.util.ArrayList;
import java.util.List;

/**
 * Maintenance / validation tool entry over the connector SPI registry (item 19 / P-REQ-28,
 * connector-design.md §8.5 D5): registration listing and capability probing. This is the
 * pre-bound runtime consumer of the registry (anti-hollow wiring) and the foundation that
 * item 20's conf-validate command will build upon.
 *
 * <p>Scope boundary: the catalog enumerates registrations and probes constructability — it
 * deliberately performs NO field-level conf validation (item 20's scope).
 *
 * <p>Container assembly is the caller's job (core stays free of a nop-ioc dependency):
 * build an {@link IBeanContainer} from the discovered {@code connector-*.beans.xml}
 * resources with the standard {@code BeanContainerBuilder} pattern, then wrap it with
 * {@link #of(IBeanContainer)}.
 */
public final class StreamConnectorCatalog {

    /** VFS directory that holds the connector factory bean definitions of all modules. */
    public static final String CONNECTOR_BEANS_VFS_PATH = "/nop/stream/beans";

    private static final String CONNECTOR_BEANS_FILE_PREFIX = "connector-";
    private static final String CONNECTOR_BEANS_FILE_SUFFIX = ".beans.xml";

    private final StreamConnectorRegistry registry;

    private StreamConnectorCatalog(StreamConnectorRegistry registry) {
        this.registry = registry;
    }

    /** Wraps a registry built by the caller (from factories or from a container). */
    public static StreamConnectorCatalog of(StreamConnectorRegistry registry) {
        if (registry == null) {
            throw new StreamException(NopStreamErrors.ERR_STREAM_NULL_ARG)
                    .param(NopStreamErrors.ARG_ARG_NAME, "registry");
        }
        return new StreamConnectorCatalog(registry);
    }

    /** Aggregates all connector factory beans of the given container into a catalog. */
    public static StreamConnectorCatalog of(IBeanContainer container) {
        return of(StreamConnectorRegistry.fromContainer(container));
    }

    /**
     * Discovers the {@code connector-*.beans.xml} resources contributed by the connector
     * modules on the classpath (VFS merge across jars). Callers feed these resources into a
     * {@code BeanContainerBuilder} to assemble the registry container. Requires initialized
     * core (VFS).
     */
    public static List<IResource> discoverConnectorBeansResources() {
        List<? extends IResource> children = VirtualFileSystem.instance()
                .getChildren(CONNECTOR_BEANS_VFS_PATH);
        List<IResource> result = new ArrayList<>();
        for (IResource child : children) {
            String name = child.getName();
            if (name != null && name.startsWith(CONNECTOR_BEANS_FILE_PREFIX)
                    && name.endsWith(CONNECTOR_BEANS_FILE_SUFFIX)) {
                result.add(child);
            }
        }
        result.sort((a, b) -> a.getName().compareTo(b.getName()));
        return result;
    }

    public StreamConnectorRegistry getRegistry() {
        return registry;
    }

    /** All capability descriptors, sorted by direction then type name. */
    public List<ConnectorCapabilityDescriptor> listConnectors() {
        return registry.listConnectors();
    }

    /** Human-readable listing of every registered connector and its capability row. */
    public String renderListing() {
        StringBuilder sb = new StringBuilder();
        sb.append("Registered nop-stream connectors (").append(registry.getRegisteredSourceTypeNames().size())
                .append(" sources, ").append(registry.getRegisteredSinkTypeNames().size()).append(" sinks):\n");
        for (ConnectorCapabilityDescriptor d : registry.listConnectors()) {
            sb.append("  [").append(d.getDirection().name()).append("] ").append(d.getTypeName());
            if (!d.getAliases().isEmpty()) {
                sb.append(" (aliases: ").append(String.join(", ", d.getAliases())).append(")");
            }
            sb.append(" -> ").append(d.getComponentClass()).append('\n');
            sb.append("      delivery=").append(d.consistencyValue())
                    .append(", parallelism=").append(d.getParallelism())
                    .append(", recovery=").append(d.getRecoverySemantic()).append('\n');
            if (!d.getParams().isEmpty()) {
                sb.append("      params: ");
                for (ConnectorParamDescriptor p : d.getParams()) {
                    sb.append(p.getName()).append(':').append(p.getKind().name())
                            .append(p.isRequired() ? "(required)" : "(optional)").append(' ');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Probes one connector: resolves the factory by type name, constructs the endpoint from
     * the given config, verifies the single-fact-source invariants (descriptor consistency
     * equals the instance's {@code getSourceConsistency()}/{@code getSinkConsistency()};
     * {@code PLANNING_GATE_PARALLELISM_1} ⟺ {@code TwoPhaseCommitSinkFunction} instance),
     * then closes the endpoint when it is {@code AutoCloseable}. Any failure throws a typed
     * exception — probes never report success silently degraded.
     */
    public ConnectorProbeResult probe(ConnectorDirection direction, String typeName, StreamConnectorConfig config) {
        if (direction == ConnectorDirection.SOURCE) {
            return probeSource(typeName, config);
        }
        return probeSink(typeName, config);
    }

    private ConnectorProbeResult probeSource(String typeName, StreamConnectorConfig config) {
        ConnectorCapabilityDescriptor descriptor = probeDescriptor(ConnectorDirection.SOURCE, typeName);
        IStreamConnectorFactory factory = registry.resolveSourceFactory(typeName);
        Object endpoint;
        boolean instanceAligned;
        if (factory instanceof IStreamSourceFunctionFactory functionFactory) {
            SourceFunction<?> fn = functionFactory.createSourceFunction(config);
            endpoint = fn;
            instanceAligned = alignConsistency(descriptor, fn.getSourceConsistency());
        } else if (factory instanceof IStreamSplitSourceFactory splitFactory) {
            endpoint = splitFactory.createSource(config);
            // split sources have no instance-level consistency accessor (§8.1); the descriptor
            // is pinned to the capability matrix and behavior tests instead
            instanceAligned = false;
        } else {
            throw new StreamException(NopStreamErrors.ERR_STREAM_CONNECTOR_DESCRIPTOR_INVALID)
                    .param(NopStreamErrors.ARG_FACTORY_CLASS, factory.getClass().getName())
                    .param(NopStreamErrors.ARG_DETAIL,
                            "resolved source factory implements no known source contract");
        }
        return finishProbe(descriptor, endpoint, instanceAligned);
    }

    private ConnectorProbeResult probeSink(String typeName, StreamConnectorConfig config) {
        ConnectorCapabilityDescriptor descriptor = probeDescriptor(ConnectorDirection.SINK, typeName);
        IStreamSinkFactory factory = registry.resolveSinkFactory(typeName);
        SinkFunction<?> fn = factory.createSink(config);

        boolean instanceAligned = alignConsistency(descriptor, fn.getSinkConsistency());
        boolean gateDeclared = descriptor.getParallelism() == ConnectorParallelism.PLANNING_GATE_PARALLELISM_1;
        boolean gateInstance = fn instanceof TwoPhaseCommitSinkFunction;
        if (gateDeclared != gateInstance) {
            throw new StreamException(NopStreamErrors.ERR_STREAM_CONNECTOR_CAPABILITY_MISMATCH)
                    .param(NopStreamErrors.ARG_TYPE_NAME, typeName)
                    .param(NopStreamErrors.ARG_DECLARED_VALUE, "parallelism="
                            + descriptor.getParallelism() + " (planning gate)")
                    .param(NopStreamErrors.ARG_ACTUAL_VALUE, "TwoPhaseCommitSinkFunction instance=" + gateInstance);
        }
        return finishProbe(descriptor, fn, instanceAligned);
    }

    private ConnectorCapabilityDescriptor probeDescriptor(ConnectorDirection direction, String typeName) {
        IStreamConnectorFactory factory = direction == ConnectorDirection.SOURCE
                ? registry.resolveSourceFactory(typeName)
                : registry.resolveSinkFactory(typeName);
        ConnectorCapabilityDescriptor descriptor = factory.describeCapabilities();
        if (!descriptor.getTypeName().equals(factory.getTypeName())) {
            throw new StreamException(NopStreamErrors.ERR_STREAM_CONNECTOR_DESCRIPTOR_INVALID)
                    .param(NopStreamErrors.ARG_FACTORY_CLASS, factory.getClass().getName())
                    .param(NopStreamErrors.ARG_DETAIL, "typeName mismatch at probe time");
        }
        return descriptor;
    }

    private boolean alignConsistency(ConnectorCapabilityDescriptor descriptor, Object instanceValue) {
        String declared = descriptor.consistencyValue();
        String actual = String.valueOf(instanceValue);
        if (!declared.equals(actual)) {
            throw new StreamException(NopStreamErrors.ERR_STREAM_CONNECTOR_CAPABILITY_MISMATCH)
                    .param(NopStreamErrors.ARG_TYPE_NAME, descriptor.getTypeName())
                    .param(NopStreamErrors.ARG_DECLARED_VALUE, declared)
                    .param(NopStreamErrors.ARG_ACTUAL_VALUE, actual);
        }
        return true;
    }

    private ConnectorProbeResult finishProbe(ConnectorCapabilityDescriptor descriptor, Object endpoint,
                                             boolean instanceAligned) {
        if (endpoint == null) {
            throw new StreamException(NopStreamErrors.ERR_STREAM_CONNECTOR_DESCRIPTOR_INVALID)
                    .param(NopStreamErrors.ARG_FACTORY_CLASS, descriptor.getComponentClass())
                    .param(NopStreamErrors.ARG_DETAIL, "factory constructed a null endpoint");
        }
        boolean autoClosed = false;
        if (endpoint instanceof AutoCloseable closeable) {
            try {
                closeable.close();
                autoClosed = true;
            } catch (Exception e) {
                throw new StreamException(NopStreamErrors.ERR_STREAM_CONNECTOR_DESCRIPTOR_INVALID, e)
                        .param(NopStreamErrors.ARG_FACTORY_CLASS, descriptor.getComponentClass())
                        .param(NopStreamErrors.ARG_DETAIL, "probe close failed: " + e.getMessage());
            }
        }
        return new ConnectorProbeResult(descriptor.getTypeName(), descriptor.getDirection(),
                endpoint.getClass().getName(), descriptor.consistencyValue(), instanceAligned, autoClosed);
    }
}
