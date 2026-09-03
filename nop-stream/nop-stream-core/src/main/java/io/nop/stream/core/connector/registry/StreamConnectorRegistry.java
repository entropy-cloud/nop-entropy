/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.registry;

import io.nop.api.core.ioc.IBeanContainer;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DIRECTION;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_REGISTERED_TYPES;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_TYPE_NAME;

/**
 * Connector SPI registry (item 19 / P-REQ-28): aggregates {@link IStreamConnectorFactory}
 * beans and resolves connector endpoints by type name in the direction-scoped namespace
 * (connector-design.md §8.2 D2 / §8.6 D6).
 *
 * <p>Aggregation: the registry is built from an iterable of factories, or from any NopIoC
 * {@link IBeanContainer} via type-based bean lookup ({@code getBeansOfType}). Factory beans
 * live in {@code connector-*.beans.xml} files under {@code _vfs/nop/stream/beans/} of the
 * connector modules and are loaded only by explicit container assembly — they never leak
 * into the global app container.
 *
 * <p>Build-time validation (fail-fast, no silent skips): duplicate type names or aliases
 * within one direction, descriptor/factory inconsistencies (type name, direction vs factory
 * contract), and missing consistency declarations are rejected with typed errors.
 *
 * <p>Unknown type names fail fast with {@code ERR_STREAM_CONNECTOR_TYPE_NOT_FOUND}
 * (including the registered-type list); resolving a name registered on the other direction
 * fails with {@code ERR_STREAM_CONNECTOR_DIRECTION_MISMATCH}. Silent fallback to the XDSL
 * bean path is forbidden.
 */
public final class StreamConnectorRegistry {

    private final Map<String, IStreamConnectorFactory> sourceFactories;
    private final Map<String, IStreamConnectorFactory> sinkFactories;

    private StreamConnectorRegistry(Map<String, IStreamConnectorFactory> sourceFactories,
                                    Map<String, IStreamConnectorFactory> sinkFactories) {
        this.sourceFactories = sourceFactories;
        this.sinkFactories = sinkFactories;
    }

    /** Builds a registry from an explicit factory iterable, validating as it goes. */
    public static StreamConnectorRegistry of(Iterable<IStreamConnectorFactory> factories) {
        if (factories == null) {
            throw new StreamException(NopStreamErrors.ERR_STREAM_NULL_ARG)
                    .param(NopStreamErrors.ARG_ARG_NAME, "factories");
        }
        Map<String, IStreamConnectorFactory> sources = new LinkedHashMap<>();
        Map<String, IStreamConnectorFactory> sinks = new LinkedHashMap<>();
        for (IStreamConnectorFactory factory : factories) {
            if (factory == null) {
                throw new StreamException(NopStreamErrors.ERR_STREAM_NULL_ARG)
                        .param(NopStreamErrors.ARG_ARG_NAME, "factory");
            }
            register(sources, sinks, factory);
        }
        return new StreamConnectorRegistry(sources, sinks);
    }

    /** Builds a registry from all {@link IStreamConnectorFactory} beans of a container. */
    public static StreamConnectorRegistry fromContainer(IBeanContainer container) {
        if (container == null) {
            throw new StreamException(NopStreamErrors.ERR_STREAM_NULL_ARG)
                    .param(NopStreamErrors.ARG_ARG_NAME, "container");
        }
        return of(container.getBeansOfType(IStreamConnectorFactory.class).values());
    }

    private static void register(Map<String, IStreamConnectorFactory> sources,
                                 Map<String, IStreamConnectorFactory> sinks,
                                 IStreamConnectorFactory factory) {
        ConnectorCapabilityDescriptor descriptor = validateDescriptor(factory);

        Map<String, IStreamConnectorFactory> target =
                descriptor.getDirection() == ConnectorDirection.SOURCE ? sources : sinks;
        String typeName = factory.getTypeName();
        IStreamConnectorFactory existing = target.get(typeName);
        if (existing != null) {
            throw duplicate(typeName, descriptor.getDirection(), existing, factory);
        }
        for (String alias : descriptor.getAliases()) {
            IStreamConnectorFactory existingAlias = target.get(alias);
            if (existingAlias != null) {
                throw duplicate(alias, descriptor.getDirection(), existingAlias, factory);
            }
        }
        target.put(typeName, factory);
        for (String alias : descriptor.getAliases()) {
            target.put(alias, factory);
        }
    }

    private static ConnectorCapabilityDescriptor validateDescriptor(IStreamConnectorFactory factory) {
        String factoryClass = factory.getClass().getName();

        ConnectorCapabilityDescriptor descriptor = factory.describeCapabilities();
        if (descriptor == null) {
            throw descriptorInvalid(factoryClass, "describeCapabilities() returned null");
        }
        if (!factory.getTypeName().equals(descriptor.getTypeName())) {
            throw descriptorInvalid(factoryClass, "typeName mismatch: factory declares '"
                    + factory.getTypeName() + "' but descriptor declares '" + descriptor.getTypeName() + "'");
        }
        if (!factory.getAliases().equals(descriptor.getAliases())) {
            throw descriptorInvalid(factoryClass, "aliases mismatch: factory declares "
                    + factory.getAliases() + " but descriptor declares " + descriptor.getAliases());
        }

        ConnectorDirection declared = descriptor.getDirection();
        boolean isSinkFactory = factory instanceof IStreamSinkFactory;
        boolean isSourceFactory = factory instanceof IStreamSourceFunctionFactory
                || factory instanceof IStreamSplitSourceFactory;
        if (isSinkFactory && declared != ConnectorDirection.SINK) {
            throw descriptorInvalid(factoryClass,
                    "sink factory must declare direction SINK, got " + declared);
        }
        if (isSourceFactory && declared != ConnectorDirection.SOURCE) {
            throw descriptorInvalid(factoryClass,
                    "source factory must declare direction SOURCE, got " + declared);
        }
        if (!isSinkFactory && !isSourceFactory) {
            throw descriptorInvalid(factoryClass,
                    "factory implements neither source nor sink factory contract");
        }
        return descriptor;
    }

    private static StreamException duplicate(String name, ConnectorDirection direction,
                                             IStreamConnectorFactory existing, IStreamConnectorFactory factory) {
        StreamException ex = new StreamException(NopStreamErrors.ERR_STREAM_CONNECTOR_DUPLICATE_TYPE);
        ex.param(ARG_DIRECTION, direction.name()).param(ARG_TYPE_NAME, name)
                .param(NopStreamErrors.ARG_EXISTING_FACTORY_CLASS, existing.getClass().getName())
                .param(NopStreamErrors.ARG_FACTORY_CLASS, factory.getClass().getName());
        return ex;
    }

    private static StreamException descriptorInvalid(String factoryClass, String detail) {
        StreamException ex = new StreamException(NopStreamErrors.ERR_STREAM_CONNECTOR_DESCRIPTOR_INVALID);
        ex.param(NopStreamErrors.ARG_FACTORY_CLASS, factoryClass).param(NopStreamErrors.ARG_DETAIL, detail);
        return ex;
    }

    // ------------------------------------------------------------------
    // resolution
    // ------------------------------------------------------------------

    /** Resolves a source factory (function or split-based) by type name or alias. */
    public IStreamConnectorFactory resolveSourceFactory(String typeName) {
        return resolve(sourceFactories, typeName, ConnectorDirection.SOURCE, sinkFactories);
    }

    /** Resolves a sink factory by type name or alias. */
    public IStreamSinkFactory resolveSinkFactory(String typeName) {
        return (IStreamSinkFactory) resolve(sinkFactories, typeName, ConnectorDirection.SINK, sourceFactories);
    }

    private static IStreamConnectorFactory resolve(Map<String, IStreamConnectorFactory> target,
                                                   String typeName, ConnectorDirection direction,
                                                   Map<String, IStreamConnectorFactory> other) {
        if (typeName == null || typeName.isEmpty()) {
            throw new StreamException(NopStreamErrors.ERR_STREAM_NULL_ARG)
                    .param(NopStreamErrors.ARG_ARG_NAME, "typeName");
        }
        IStreamConnectorFactory factory = target.get(typeName);
        if (factory != null) {
            return factory;
        }
        if (other.containsKey(typeName)) {
            throw new StreamException(NopStreamErrors.ERR_STREAM_CONNECTOR_DIRECTION_MISMATCH)
                    .param(ARG_TYPE_NAME, typeName)
                    .param(NopStreamErrors.ARG_ACTUAL_DIRECTION, other.get(typeName)
                            .describeCapabilities().getDirection().name())
                    .param(NopStreamErrors.ARG_EXPECTED_DIRECTION, direction.name());
        }
        throw new StreamException(NopStreamErrors.ERR_STREAM_CONNECTOR_TYPE_NOT_FOUND)
                .param(ARG_TYPE_NAME, typeName)
                .param(ARG_DIRECTION, direction.name())
                .param(ARG_REGISTERED_TYPES, new ArrayList<>(registeredNames(target)));
    }

    private static Collection<String> registeredNames(Map<String, IStreamConnectorFactory> target) {
        // only primary type names (no aliases) are listed as the registered set
        TreeSet<String> names = new TreeSet<>();
        for (IStreamConnectorFactory factory : target.values()) {
            names.add(factory.getTypeName());
        }
        return names;
    }

    // ------------------------------------------------------------------
    // enumeration
    // ------------------------------------------------------------------

    /** All registered source type names (primary names only, sorted). */
    public List<String> getRegisteredSourceTypeNames() {
        return List.copyOf(registeredNames(sourceFactories));
    }

    /** All registered sink type names (primary names only, sorted). */
    public List<String> getRegisteredSinkTypeNames() {
        return List.copyOf(registeredNames(sinkFactories));
    }

    /** All capability descriptors, sorted by direction then type name (alias entries excluded). */
    public List<ConnectorCapabilityDescriptor> listConnectors() {
        List<ConnectorCapabilityDescriptor> descriptors = new ArrayList<>();
        Set<IStreamConnectorFactory> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IStreamConnectorFactory factory : sourceFactories.values()) {
            if (seen.add(factory)) {
                descriptors.add(factory.describeCapabilities());
            }
        }
        for (IStreamConnectorFactory factory : sinkFactories.values()) {
            if (seen.add(factory)) {
                descriptors.add(factory.describeCapabilities());
            }
        }
        descriptors.sort(Comparator.comparing(ConnectorCapabilityDescriptor::getDirection)
                .thenComparing(ConnectorCapabilityDescriptor::getTypeName));
        return descriptors;
    }
}
