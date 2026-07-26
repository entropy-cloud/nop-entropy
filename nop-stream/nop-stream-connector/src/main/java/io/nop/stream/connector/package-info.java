/**
 * Base stream connectors for external data sources that have no heavyweight
 * optional dependencies.
 *
 * <p>This module provides connector implementations that depend only on
 * {@code nop-api-core}'s {@code IMessageService} abstraction (pulled in
 * transitively by {@code nop-stream-core}):</p>
 *
 * <ul>
 *   <li>{@link io.nop.stream.connector.MessageSourceFunction} — adapts
 *       {@code IMessageService} subscriptions to a stream source.</li>
 *   <li>{@link io.nop.stream.connector.MessageSinkFunction} — adapts
 *       {@code IMessageService} publishing to a stream sink.</li>
 * </ul>
 *
 * <p><strong>AR-2 module split</strong> — connectors that require optional
 * runtime libraries have been extracted into separate modules so that this
 * base module stays loadable when those libraries are absent:</p>
 *
 * <ul>
 *   <li>{@code nop-stream-connector-batch} — {@code BatchLoaderSourceFunction},
 *       {@code BatchConsumerSinkFunction}, {@code StreamConnectors}
 *       (depends on {@code nop-batch-core}).</li>
 *   <li>{@code nop-stream-connector-debezium} —
 *       {@code DebeziumCdcSourceFunction} (depends on
 *       {@code nop-message-debezium}).</li>
 * </ul>
 *
 * @see io.nop.stream.connector.MessageSourceFunction
 * @see io.nop.stream.connector.MessageSinkFunction
 */
package io.nop.stream.connector;
