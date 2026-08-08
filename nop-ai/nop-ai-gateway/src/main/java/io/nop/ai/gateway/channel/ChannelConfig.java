package io.nop.ai.gateway.channel;

import java.util.HashMap;
import java.util.Map;

/**
 * Channel configuration carrier handed to a connector via
 * {@link ChannelConnectorContext}. Holds the agent name this channel routes
 * to plus an arbitrary options map (vendor credentials, polling intervals,
 * etc.) that each connector interprets in its own terms.
 *
 * <p>Credentials carried in {@link #getOptions()} are expected to be resolved
 * from encrypted configuration sources (e.g. {@code @InjectValue} +
 * {@code nop-config-encrypt}) by the wiring layer before being placed here;
 * this object never performs decryption itself.
 */
public class ChannelConfig {

    private String agentName;
    private Map<String, Object> options = new HashMap<>();

    public ChannelConfig() {
    }

    public ChannelConfig(String agentName) {
        this.agentName = agentName;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options != null ? options : new HashMap<>();
    }

    public Object getOption(String key) {
        return options.get(key);
    }

    public void setOption(String key, Object value) {
        options.put(key, value);
    }
}
