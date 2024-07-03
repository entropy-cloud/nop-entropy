# 消息服务
Nop平台中对于消息系统的抽象统一通过IMessageService接口来提供。IMessageService提供异步发送和接受消息的能力。

使用时需要引入nop-message-core模块。

## 核心接口

```java
interface IMessageService{
     CompletionStage<Void> sendAsync(String topic, Object message,
                   MessageSendOptions options);

    /**
     * 响应消息发送到一个相关的topic上
     *
     * @param topic 请求消息所属的topic
     * @return reply消息所对应的队列
     */
    default String getReplyTopic(String topic) {
        return "reply-" + topic;
    }

    IMessageSubscription subscribe(String topic, IMessageConsumer listener,
            MessageSubscribeOptions options);
}
```

* 消息系统将消息发送到指定topic。对于Pulsar和Kafka消息系统，可以直接对应于消息系统内部的topic。

## IoC集成
在Java类上标注 `@MessageSubscription`注解并注册到IoC容器中以后，可以在方法上标注`@MessageListener`接口来监听消息。

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

NopRPC在设计上定位为发送和接收配对的消息处理，因此它可以运行在IMessageService接口之上。只要底层提供一个IMessageService，我们就可以将它封装为IRpcService接口来使用。
