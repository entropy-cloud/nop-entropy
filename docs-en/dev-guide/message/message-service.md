# Message Service
In the Nop platform, the unified abstraction for the messaging system is provided via the IMessageService interface. IMessageService provides the capability to send and receive messages asynchronously.

To use it, include the nop-message-core module.

## Core Interface

```java
interface IMessageService{
     CompletionStage<Void> sendAsync(String topic, Object message,
                   MessageSendOptions options);

    /**
     * Reply messages are sent to a related topic
     *
     * @param topic The topic to which the request message belongs
     * @return The queue corresponding to the reply message
     */
    default String getReplyTopic(String topic) {
        return "reply-" + topic;
    }

    IMessageSubscription subscribe(String topic, IMessageConsumer listener,
            MessageSubscribeOptions options);
}
```

* The messaging system sends messages to a specified topic. For Pulsar and Kafka, this can directly map to the internal topics of the messaging system.

## IoC Integration
After annotating a Java class with `@MessageSubscription` and registering it in the IoC container, you can annotate methods with the `@MessageListener` annotation to listen for messages.

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

NopRPC is designed for paired send-and-receive message processing, so it can run on top of the IMessageService interface. As long as the underlying layer provides an IMessageService, we can wrap it as an IRpcService for use.

<!-- SOURCE_MD5:d9c3eeb6aa3361cc1d0b3296fb3b12e6-->
