package io.nop.ai.agent.router;

import io.nop.ai.api.chat.ChatOptions;

import java.util.Objects;

public final class RoutingResult {

    private final ChatOptions options;
    private final String complexity;
    private final String routingReason;

    public RoutingResult(ChatOptions options, String complexity, String routingReason) {
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.complexity = complexity;
        this.routingReason = routingReason;
    }

    public ChatOptions getOptions() {
        return options;
    }

    public String getComplexity() {
        return complexity;
    }

    public String getRoutingReason() {
        return routingReason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoutingResult that = (RoutingResult) o;
        return Objects.equals(options, that.options)
                && Objects.equals(complexity, that.complexity)
                && Objects.equals(routingReason, that.routingReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(options, complexity, routingReason);
    }

    @Override
    public String toString() {
        return "RoutingResult{options=" + options
                + ", complexity='" + complexity + "'"
                + ", routingReason='" + routingReason + "'}";
    }
}
