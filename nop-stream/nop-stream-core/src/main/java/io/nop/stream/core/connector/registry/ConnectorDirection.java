/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.registry;

/**
 * Direction of a registered connector endpoint (item 19 / P-REQ-28). The connector
 * type-name namespace is direction-scoped: {@code (direction, typeName)} is the unique
 * registration key, so {@code file} exists independently as a source and as a sink type.
 */
public enum ConnectorDirection {
    SOURCE,
    SINK
}
