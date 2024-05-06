package io.nop.message.core;

import io.nop.api.core.annotations.message.MessageListener;
import io.nop.api.core.annotations.message.MessageSubscription;
import io.nop.api.core.util.Guard;

import static io.nop.message.core.MessageCoreConstants.BEAN_LOCAL_MESSAGE_SERVICE;

@MessageSubscription
public class MyMessageListener {
    private int triggerCount = 0;

    public int getTriggerCount() {
        return triggerCount;
    }

    @MessageListener(topic = "test", messageServiceBean = BEAN_LOCAL_MESSAGE_SERVICE)
    public void handleMessage(MyMessage message) {
        Guard.notNull(message, "message");
        triggerCount++;
    }
}
