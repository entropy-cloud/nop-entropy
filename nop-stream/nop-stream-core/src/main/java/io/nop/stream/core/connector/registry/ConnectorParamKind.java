/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.registry;

/**
 * Kind of a connector construction parameter. {@link #OBJECT} parameters are code or
 * infrastructure artifacts (e.g. {@code IMessageService}, {@code IJdbcTemplate},
 * {@code DebeziumConfig}, a record-mapper function) that must be supplied programmatically;
 * they are the seed of item 20's field-level conf validation surface.
 */
public enum ConnectorParamKind {
    STRING,
    INT,
    STRING_LIST,
    OBJECT
}
