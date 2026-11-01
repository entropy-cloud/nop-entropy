package io.nop.http.api.utils;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C6-006 (plan 2523, MA-03 吸收成员)：sidecar get 超时取消必须经 promise→底层取消桥接
 * 触达传输层——三实现 fetchAsync 的 promise 均不向上游传播取消（jdk thenApply 派生 stage/
 * okhttp·apache 裸 CF），须显式桥接。
 *
 * <p>证据形态：源码级结构钉（2522 R2 Major-3 先例——行为探针无法区分「桥接在位」与
 * 「仅 get 超时不取消」，且传输层取消可观测性受 server 实现约束）。红证 = 缺陷基线
 * 三处桥接/取消行缺位。</p>
 */
public class TestSidecarCancelBridge_C6_006 {

    private static String read(String module, String rel) throws Exception {
        Path p = Paths.get("nop-network/nop-http/" + module + "/src/main/java/" + rel);
        if (!Files.exists(p)) {
            // 单模块 surefire 工作目录回退：../<module>/src/main/...
            p = Paths.get("../" + module + "/src/main/java/" + rel);
        }
        if (!Files.exists(p)) {
            // 仓库根回退
            p = Paths.get("nop-network/nop-http/" + module + "/src/main/java/" + rel);
        }
        return Files.readString(p);
    }

    @Test
    public void okhttpPromiseToCallBridgeAndSidecarCancelInPlace() throws Exception {
        String code = read("nop-http-client-okhttp",
                "io/nop/http/client/okhttp/OkHttpClientImpl.java");
        assertTrue(code.contains("if (future.isCancelled())") && code.contains("call.cancel();"),
                "C6-006: okhttp fetchAsync must bridge promise-cancellation to call.cancel()");
        assertTrue(code.contains("future.cancel(false);"),
                "C6-006: okhttp sidecar get-timeout must cancel the in-flight request");
    }

    @Test
    public void jdkPromiseToRawStageBridgeAndSidecarCancelInPlace() throws Exception {
        String code = read("nop-http-client-jdk",
                "io/nop/http/client/jdk/JdkHttpClient.java");
        assertTrue(code.contains("raw.cancel(true)"),
                "C6-006: jdk fetchAsync must bridge promise-cancellation to the RAW sendAsync"
                        + " stage (thenApply-derived stage cancel never propagates upstream)");
        assertTrue(code.contains("future.cancel(false);"),
                "C6-006: jdk sidecar get-timeout must cancel the in-flight request");
    }

    @Test
    public void apachePromiseToFutureBridgeAndSidecarCancelInPlace() throws Exception {
        String code = read("nop-http-client-apache",
                "io/nop/http/apache/ApacheHttpClient.java");
        assertTrue(code.contains("if (promise.isCancelled())") && code.contains("future.cancel(false);"),
                "C6-006: apache fetchAsync must bridge promise-cancellation to HC5 future.cancel()");
        assertTrue(code.contains("future.cancel(false);"),
                "C6-006: apache sidecar get-timeout must cancel the in-flight request");
    }

    @Test
    public void apacheSidecarOffloadedToWorkerPool() throws Exception {
        String code = read("nop-http-client-apache",
                "io/nop/http/apache/ApacheHttpClient.java");
        assertTrue(code.contains("GlobalExecutors.globalWorker()"),
                "C3-004: apache sidecar probing must run off the HC5 reactor callback thread"
                        + " (ioThreadCount=1 config = whole-reactor stall, CL-021)");
    }
}
