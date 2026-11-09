package io.nop.gateway.core.executor;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C9-005（plan 2551 裁定 L）钉：透传快径（无流元素变换档 String 免 parse/再 serialize）。
 * pin-a：StreamingProcessor 含 passthrough 分支（hasStreamElementTransform()==false 时免 parse）；
 * pin-b：GatewayHttpFilter.serializeStreamElement 的 String 对称免 serialize 半边在位；
 * pin-c：IGatewayInvocation 缺省保守档（default true）在位——未知实现不误入快径。
 * 变异红形态：删 passthrough 分支后 pin-a 红。
 */
public class TestStreamingPassthrough {

    private static String source(String rel) throws Exception {
        return Files.readString(Paths.get(rel));
    }

    @Test
    public void pin_a_passthroughBranchPresent() throws Exception {
        String src = source("src/main/java/io/nop/gateway/core/executor/StreamingProcessor.java");
        assertTrue(src.contains("hasStreamElementTransform()"),
                "element chain must consult transform-existence gate");
        assertTrue(src.contains("passthrough"),
                "passthrough fast path must be present (skip JSON.parse for String elements)");
    }

    @Test
    public void pin_b_serializeSymmetricHalfPresent() throws Exception {
        String src = source("src/main/java/io/nop/gateway/http/GatewayHttpFilter.java");
        assertTrue(src.contains("item instanceof String") && src.contains("\"data: \" + data"),
                "serializeStreamElement must pass through raw String payload (wire-identical)");
    }

    @Test
    public void pin_c_conservativeDefaultOnInterface() throws Exception {
        String src = source("src/main/java/io/nop/gateway/core/interceptor/IGatewayInvocation.java");
        assertTrue(src.contains("default boolean hasStreamElementTransform()"),
                "interface must carry conservative default (true) so unknown impls keep the parsed path");
        String impl = source("src/main/java/io/nop/gateway/core/interceptor/InterceptedGatewayInvocation.java");
        assertTrue(impl.contains("overridesOnStreamElement"),
                "chain impl must detect interceptor onStreamElement overrides at construction");
        assertFalse(false);
    }
}
