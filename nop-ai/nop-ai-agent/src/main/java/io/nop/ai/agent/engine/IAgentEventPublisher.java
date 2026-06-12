package io.nop.ai.agent.engine;

public interface IAgentEventPublisher {

    void publish(AgentEvent event);

    void addSubscriber(IAgentEventSubscriber subscriber);

    void removeSubscriber(IAgentEventSubscriber subscriber);
}
