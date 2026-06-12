package io.nop.ai.agent.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DefaultAgentEventPublisher implements IAgentEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAgentEventPublisher.class);

    private final List<IAgentEventSubscriber> subscribers = new CopyOnWriteArrayList<>();

    @Override
    public void publish(AgentEvent event) {
        for (IAgentEventSubscriber subscriber : subscribers) {
            try {
                subscriber.onEvent(event);
            } catch (Exception e) {
                LOG.error("Subscriber {} threw exception while handling event {}",
                        subscriber.getClass().getName(), event.getEventType(), e);
            }
        }
    }

    @Override
    public void addSubscriber(IAgentEventSubscriber subscriber) {
        subscribers.add(subscriber);
    }

    @Override
    public void removeSubscriber(IAgentEventSubscriber subscriber) {
        subscribers.remove(subscriber);
    }
}
