/**
 * Stream connectors for external data sources.
 *
 * <p>This module provides built-in source/sink connector implementations.
 * Some connectors depend on optional libraries that are not transitively
 * included:</p>
 *
 * <ul>
 *   <li>{@code MessageSourceFunction} / {@code MessageSinkFunction}:
 *       depends on {@code nop-api-core} ({@code IMessageService}) at compile time.
 *       At runtime, a {@code IMessageService} implementation (e.g., {@code nop-message-core})
 *       must be provided.</li>
 *   <li>Debezium CDC adapter:
 *       requires {@code nop-message-debezium} on the classpath at runtime.</li>
 * </ul>
 *
 * @see io.nop.stream.connector.MessageSourceFunction
 */
package io.nop.stream.connector;
