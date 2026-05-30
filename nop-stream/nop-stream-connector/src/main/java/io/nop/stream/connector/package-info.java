/**
 * Stream connectors for external data sources.
 *
 * <p>This module provides built-in source/sink connector implementations.
 * Some connectors depend on optional libraries that are not transitively
 * included:</p>
 *
 * <ul>
 *   <li>{@code MessageSourceFunction} / {@code MessageSinkFunction}:
 *       requires {@code nop-message-core} on the classpath at runtime.</li>
 *   <li>Debezium CDC adapter:
 *       requires {@code nop-message-debezium} on the classpath at runtime.</li>
 * </ul>
 *
 * @see io.nop.stream.connector.MessageSourceFunction
 */
package io.nop.stream.connector;
