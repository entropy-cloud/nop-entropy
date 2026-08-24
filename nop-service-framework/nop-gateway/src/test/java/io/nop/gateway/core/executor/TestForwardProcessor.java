package io.nop.gateway.core.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.model.GatewayModel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ForwardProcessor.forward 的 null 防御分支不得返回 null（调用方 thenCompose 链式使用会 NPE），
 * 必须显式抛出编程错误异常。
 */
class TestForwardProcessor {

    @Test
    void forward_nullConfig_throwsIllegalArgument() {
        ForwardProcessor processor = new ForwardProcessor(new GatewayModel());
        assertThrows(IllegalArgumentException.class,
                () -> processor.forward(null, ApiRequest.build(Map.of()), new GatewayContextImpl(), r -> null),
                "forward(null,...) 是编程错误，必须 fail-loud 而不是返回 null 导致下游 NPE");
    }
}
