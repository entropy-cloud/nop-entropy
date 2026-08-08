package io.nop.ai.gateway.channel;

import io.nop.api.core.exceptions.NopException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.api.core.ApiErrors.ERR_CHECK_INVALID_ARGUMENT;

/**
 * Registry and unified lifecycle owner for all {@link IChannelConnector}
 * instances in the gateway. Connectors are collected from the Nop IoC
 * container (via {@code <ioc:collect-beans by-type="...IChannelConnector"/>}
 * in {@code ai-gateway-defaults.beans.xml}) and may also be registered
 * programmatically (used by tests).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li><b>Lookup</b> by {@code channelType} — used by the business message
 *       layer (Plan 3) to route an outbound message to the right connector.
 *       An unknown {@code channelType} throws rather than returning
 *       {@code null} (no silent miss).</li>
 *   <li><b>Unified lifecycle</b> — {@link #startAll} starts every registered
 *       connector with the same context (in registration order);
 *       {@link #stopAll} stops them in reverse order. A connector that fails
 *       to start surfaces the exception (no silent {@code continue}).</li>
 * </ul>
 *
 * <p>Anti-Hollow: this manager REALLY invokes {@code connector.start(ctx)} /
 * {@code connector.stop()} on each registered connector at runtime — the
 * stub-connector test (W1) asserts call counts &gt; 0, proving the wiring is
 * not a type-only registration.
 */
public class ChannelConnectorManager {

    private static final String ARG_CHANNEL_TYPE = "channelType";

    private final List<IChannelConnector> connectors = new ArrayList<>();
    private final Map<String, IChannelConnector> byType = new LinkedHashMap<>();
    private final List<IChannelConnector> started = new ArrayList<>();

    /**
     * Collect connectors from the IoC container (wired via
     * {@code <ioc:collect-beans>} in the beans definition).
     */
    public void setConnectors(Collection<IChannelConnector> connectors) {
        this.connectors.clear();
        this.byType.clear();
        if (connectors != null) {
            for (IChannelConnector c : connectors) {
                register(c);
            }
        }
    }

    /**
     * Programmatically register a connector. Rejects duplicates and null.
     */
    public void register(IChannelConnector connector) {
        if (connector == null) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("msg",
                    "ChannelConnectorManager.register: connector must not be null");
        }
        String type = connector.getChannelType();
        if (type == null || type.isEmpty()) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("msg",
                    "ChannelConnectorManager.register: connector.getChannelType() must not be null/empty");
        }
        if (byType.containsKey(type)) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param(ARG_CHANNEL_TYPE, type).param("msg",
                    "ChannelConnectorManager.register: duplicate connector for channelType=" + type);
        }
        connectors.add(connector);
        byType.put(type, connector);
    }

    public IChannelConnector lookup(String channelType) {
        IChannelConnector c = byType.get(channelType);
        if (c == null) {
            // explicit miss, never null-tolerant silent return
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param(ARG_CHANNEL_TYPE, channelType).param("msg",
                    "ChannelConnectorManager.lookup: no connector registered for channelType=" + channelType);
        }
        return c;
    }

    public List<IChannelConnector> getConnectors() {
        return Collections.unmodifiableList(connectors);
    }

    /**
     * Start every registered connector with the given context, in
     * registration order. Each connector's {@code start} receives the
     * non-null context; a failure surfaces immediately (no silent skip).
     */
    public void startAll(ChannelConnectorContext context) {
        if (context == null) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("msg",
                    "ChannelConnectorManager.startAll: context must not be null");
        }
        for (IChannelConnector c : connectors) {
            c.start(context);
            started.add(c);
        }
    }

    /**
     * Stop every connector that was started, in reverse start order
     * (LIFO — mirrors typical resource-release ordering).
     */
    public void stopAll() {
        for (int i = started.size() - 1; i >= 0; i--) {
            started.get(i).stop();
        }
        started.clear();
    }
}
