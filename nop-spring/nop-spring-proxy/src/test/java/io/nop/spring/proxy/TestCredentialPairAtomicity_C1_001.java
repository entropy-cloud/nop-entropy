package io.nop.spring.proxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


/**
 * C1-001（plan 2524）：凭据对快照原子性钉——写线程整体替换 Credentials，读线程读到的
 * cookie/authorization 恒同代（不可变快照语义下构造性保证，非竞态碰运气）。
 * 兼容收口义务（R1 Major-F2）：legacy pair 存取器已移除，钉驱动器以新 API 驱动。
 */
public class TestCredentialPairAtomicity_C1_001 {

    @Test
    public void snapshotReadAlwaysSeesSameGenerationPair() throws Exception {
        WebClientHolder holder = new WebClientHolder();
        holder.setCredentials(new WebClientHolder.Credentials("Bearer token-1", "cookie-1"));

        // 写线程：代际 1→2→3 连续整体替换（构造性交错——非竞态碰运气）
        CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
            for (int gen = 2; gen <= 100; gen++) {
                holder.setCredentials(new WebClientHolder.Credentials(
                        "Bearer token-" + gen, "cookie-" + gen));
            }
        });

        // 读线程：持续读快照，断言同代
        CompletableFuture<Boolean> reader = CompletableFuture.supplyAsync(() -> {
            for (int i = 0; i < 10_000; i++) {
                WebClientHolder.Credentials snapshot = holder.getCredentials();
                if (snapshot == null)
                    continue;
                String auth = snapshot.getAuthorization();
                String cookie = snapshot.getCookie();
                // 同代判定：token-N 与 cookie-N 代号一致
                if (auth != null && cookie != null
                        && !auth.replace("Bearer token-", "").equals(cookie.replace("cookie-", ""))) {
                    return false; // 撕裂：跨代错配
                }
            }
            return true;
        });

        writer.get(10, TimeUnit.SECONDS);
        assertTrue(reader.get(10, TimeUnit.SECONDS),
                "C1-001: every read must see a same-generation cookie/authorization pair"
                        + " (defect: pair setters write separately — Reactor thread write + request"
                        + " thread read can observe cookie#2+token#1 generation mismatch)");
    }

    @Test
    public void pairAccessorsAreRemoved() {
        // 兼容收口义务（R1 Major-F2）：legacy pair 存取器已移除——消费面闭合为同文件
        assertThrowsNoSuchMethod("setAuthorization");
        assertThrowsNoSuchMethod("setCookie");
        assertThrowsNoSuchMethod("getAuthorization");
        assertThrowsNoSuchMethod("getCookie");
    }

    private void assertThrowsNoSuchMethod(String name) {
        try {
            WebClientHolder.class.getMethod(name, String.class);
            throw new AssertionError(
                    "legacy pair setter " + name + " must be removed (R1 Major-F2 兼容收口)");
        } catch (NoSuchMethodException expected) {
            // removed
        }
        try {
            WebClientHolder.class.getMethod(name);
            throw new AssertionError(
                    "legacy pair getter " + name + " must be removed (R1 Major-F2 兼容收口)");
        } catch (NoSuchMethodException expected) {
            // removed
        }
    }
}
