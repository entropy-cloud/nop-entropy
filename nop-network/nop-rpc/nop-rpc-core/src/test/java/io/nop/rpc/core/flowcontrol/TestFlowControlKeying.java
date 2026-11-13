package io.nop.rpc.core.flowcontrol;

import io.nop.rpc.api.flowcontrol.FlowControlEntry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C8-008（plan 2551 裁定 I）钉：流控 runner 按 resource 分桶（IRateLimiter 原语零换型）。
 * pin-a：不同 resource 独立桶互不稀释（A 桶耗尽不影响 B 桶）；
 * pin-b：同 resource 共享桶（连续消耗累积）；
 * pin-c：setter 置新速率后 buckets 重建（旧耗尽态不残留）；
 * 变异红形态：单桶形态（entry 参数忽略）下 pin-a 红。
 */
public class TestFlowControlKeying {

    private static FlowControlEntry entry(String type, String resource) {
        FlowControlEntry entry = new FlowControlEntry();
        entry.setResourceType(type);
        entry.setResource(resource);
        return entry;
    }

    @Test
    public void pin_a_distinctResourcesDoNotDiluteEachOther() {
        DefaultFlowControlRunner runner = new DefaultFlowControlRunner(2.0); // 2 QPS/桶
        runner.setMaxWaitMillis(0);

        // 桶 A 连续消耗至拒绝
        int aAccepted = 0;
        for (int i = 0; i < 50; i++) {
            try {
                runner.run(entry("web", "/svc-a"), () -> "ok");
                aAccepted++;
            } catch (Exception e) {
                break;
            }
        }
        assertTrue(aAccepted < 50, "bucket A must eventually reject (rate bounded)");

        // 桶 B（不同 resource）不受 A 拒绝态影响：立即可用
        String result = runner.run(entry("web", "/svc-b"), () -> "ok-b");
        assertEquals("ok-b", result, "distinct resource must have an independent bucket");
    }

    @Test
    public void pin_b_sameResourceSharesBucket() {
        DefaultFlowControlRunner runner = new DefaultFlowControlRunner(1.0);
        runner.setMaxWaitMillis(0);
        // 同 resource 连续打满：第二次尝试大概率被限速（1 QPS）
        int accepted = 0;
        for (int i = 0; i < 20; i++) {
            try {
                runner.run(entry("web", "/svc-c"), () -> "ok");
                accepted++;
            } catch (Exception e) {
                break;
            }
        }
        assertTrue(accepted < 20, "same resource must share one bucket (rate bounded)");
    }

    @Test
    public void pin_c_setterRebuildsBuckets() throws Exception {
        DefaultFlowControlRunner runner = new DefaultFlowControlRunner(0.0001); // 极低速率
        runner.setMaxWaitMillis(0);
        // 连续消耗至桶拒绝（初始突发 token 用尽）
        boolean rejected = false;
        for (int i = 0; i < 100; i++) {
            try {
                runner.run(entry("gateway", "/chat"), () -> "ok");
            } catch (Exception e) {
                rejected = true;
                break;
            }
        }
        assertTrue(rejected, "exhausted bucket must reject at 0.0001 QPS");

        // 置高速率后：桶重建，不再拒绝
        runner.setMaxPermitsPerSecond(10_000.0);
        assertEquals("ok", runner.run(entry("gateway", "/chat"), () -> "ok"),
                "rate setter must rebuild buckets (exhausted state must not persist)");

        // 未配置速率（裸构造）：不设流控档——显式语义，直接执行
        DefaultFlowControlRunner bare = new DefaultFlowControlRunner();
        bare.setMaxWaitMillis(0);
        assertEquals("ok", bare.run(entry("web", "/x"), () -> "ok"));
    }
}
