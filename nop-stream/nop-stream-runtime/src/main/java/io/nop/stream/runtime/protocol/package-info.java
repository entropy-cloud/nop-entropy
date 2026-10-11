/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
/**
 * Cross-JVM wire contract types for the nop-stream control plane and deployment path
 * (WI6 plan 2416).
 *
 * <p>This package is a zero-out-edge leaf: its types depend only on
 * {@code io.nop.stream.core}, {@code io.nop.stream.runtime.cluster} and the JDK.
 * It must NEVER import from the transport / rpc / taskmanager / coordinator
 * packages — those four depend on this leaf, and a reverse import would
 * re-create the package cycles this extraction eliminated. The one-off import
 * cycle scan lives in the WI6 plan record; WI12 promotes it to a CI gate.
 *
 * <p>Wire-compatibility contract: these types cross JVMs via (a) the nop RPC
 * message path, which encodes by argument name and decodes against the static
 * service signature (package rename is wire-safe), and (b) Java serialization in
 * PollingJdbcMessageService, which embeds the class FQN — same-version
 * deployments round-trip unchanged (serialVersionUIDs preserved); cross-version
 * residual is recorded in the WI6 plan.
 */
package io.nop.stream.runtime.protocol;
