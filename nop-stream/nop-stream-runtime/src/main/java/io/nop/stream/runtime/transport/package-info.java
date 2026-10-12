/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
/**
 * Cross-JVM data plane: input channels / result partitions, queue gauges, topic naming ({@link io.nop.stream.runtime.transport.StreamTopicNaming}) and the wire codec family (identity / string codecs over Kafka / Pulsar / SysDao).
 *
 * <p>Must stay acyclic against rpc / taskmanager / coordinator: wire DTOs come from the {@code protocol} leaf (WI6 plan 2416).
 */
package io.nop.stream.runtime.transport;
