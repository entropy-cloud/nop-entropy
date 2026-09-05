/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.registry;

import java.util.List;

/**
 * Base contract of a registered connector factory (item 19 / P-REQ-28). Factories are
 * stateless: every construction input arrives per call through {@link StreamConnectorConfig}
 * (scalar params plus programmatic OBJECT params). Implementations are discovered as NopIoC
 * beans ({@code connector-*.beans.xml} under {@code _vfs/nop/stream/beans/}) — no annotation
 * scanning.
 *
 * <p>Three concrete factory forms exist, split by the endpoint execution contract
 * (connector-design.md §8.1 D1): {@link IStreamSourceFunctionFactory} (push-model
 * {@code SourceFunction} endpoints), {@link IStreamSplitSourceFactory} (FLIP-27 split-based
 * {@code Source} endpoints), and {@link IStreamSinkFactory} ({@code SinkFunction} endpoints).
 */
public interface IStreamConnectorFactory {

    /** Connector type name in the direction-scoped namespace (kebab-case family name). */
    String getTypeName();

    /**
     * Alias names resolvable to this factory. Type-name exact match takes precedence over
     * alias resolution. All built-in factories declare no aliases; the mechanism is reserved
     * for future renames/compatibility names.
     */
    default List<String> getAliases() {
        return List.of();
    }

    /** Capability declaration of the endpoint this factory constructs; never null. */
    ConnectorCapabilityDescriptor describeCapabilities();
}
