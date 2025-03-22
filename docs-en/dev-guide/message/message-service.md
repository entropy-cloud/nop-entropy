# Message Service
In the Nop platform, messaging is abstracted and unified through the IMessageService interface. The IMessageService provides asynchronous sending and receiving capabilities.

When using it, you need to import the nop-message-core module.

## Core Interfaces

```java
interface IMessageService {
    CompletionStage<Void> sendAsync(String topic, Object message,
                                 MessageSendOptions options);

    /**
     * Responds to a message being sent to a relevant topic.
     *
     * @param topic The topic associated with the message
     * @return The reply queue corresponding to the message
     */
    default String getReplyTopic(String topic) {
        return "reply-" + topic;
    }

    IMessageSubscription subscribe(String topic, IMessageConsumer listener,
                               MessageSubscribeOptions options);
}
```

* The messaging system sends messages to a specified topic. For Pulsar and Kafka messaging systems, this corresponds directly to the internal topics of the system.

## IoC Integration
To integrate with the dependency injection container in Java classes, annotate the class with `@MessageSubscription` and the method with `@MessageListener`. Register the class with the IoC container.

```java
@MessageSubscription
public class MyMessageListener {
    @MessageListener(topic = "test", messageServiceBean = BEAN_LOCAL_MESSAGE_SERVICE)
    public void handleMessage(MyMessage message) {
        Guard.notNull(message, "message");
        triggerCount++;
    }
}
```

## RPC over MessageService

NopRPC is designed to handle paired sending and receiving of messages. It can run on top of the IMessageService interface. As long as the underlying implementation provides an IMessageService, it can be encapsulated into an IRpcService interface for use.
